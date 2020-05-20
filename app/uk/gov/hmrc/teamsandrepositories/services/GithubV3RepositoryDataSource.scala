/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories.services

import java.util.concurrent.Executors

import cats.implicits._
import com.codahale.metrics.MetricRegistry
import org.yaml.snakeyaml.Yaml
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.githubclient._
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.helpers.RetryStrategy._
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


class GithubV3RepositoryDataSource(
  githubConfig              : GithubConfig,
  val githubApiClient       : GithubApiClient,
  githubConnector           : GithubConnector,
  timestampF                : () => Long,
  val defaultMetricsRegistry: MetricRegistry,
  repositoriesToIgnore      : List[String],
  futureHelpers             : FutureHelpers
) {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  private val logger = Logger(this.getClass)

  implicit val repositoryFormats     = Json.format[GitRepository]
  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  val retries: Int              = 5
  val initialDuration: Duration = 50.millis

  val HMRC_ORG = "hmrc"

  def getTeamsForHmrcOrg: Future[List[GhTeam]] =
    withCounter(s"github.open.teams") {
      githubApiClient.getTeamsForOrganisation(HMRC_ORG)
    }.map(_.filterNot(team => githubConfig.hiddenTeams.contains(team.name)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve teams for organisation list.", ex)
          Future.failed(ex)
      }

  def mapTeam(team: GhTeam, persistedTeams: Seq[TeamRepositories]): Future[TeamRepositories] = {
    logger.debug(s"Mapping team (${team.name})")
    exponentialRetry(retries, initialDuration) {
      withCounter(s"github.open.repos") {
        githubApiClient.getReposForTeam(team.id)
      }.flatMap { repos =>
        val nonHiddenRepos = repos
          .filterNot(repo => githubConfig.hiddenRepositories.contains(repo.name))

        val updatedRepositories: Future[Seq[GitRepository]] =
          nonHiddenRepos.foldLeftM(Seq.empty[GitRepository]){ case (acc, repo) =>
            mapRepository(team, repo, persistedTeams)
              .map(acc :+ _)
          }

        updatedRepositories.map { repos =>
          TeamRepositories(team.name, repositories = repos.toList, timestampF())
        }
      }
    }.recover {
      case e =>
        logger.error("Could not map teams with organisations.", e)
        throw e
    }
  }

  def getAllRepositories(): Future[List[GitRepository]] = {
    withCounter(s"github.open.allRepos") {
      githubApiClient.getReposForOrg(HMRC_ORG)
        .map(_.map(r => buildGitRepository(r, RepoType.Other, None, Seq.empty)))
    }.recoverWith {
      case NonFatal(ex) =>
        logger.error("Could not retrieve repo list for organisation.", ex)
        Future.failed(ex)
    }
  }

  def getRepositoryDetailsFromGithub(repository: GhRepository): Future[GitRepository] =
    for {
      optManifest     <- githubConnector.getFileContent(repository.name, "repository.yaml")
      manifestDetails =  optManifest.flatMap(parseManifest(repository.name, _))
                           .getOrElse(ManifestDetails(None, None, Nil))
      repositoryType  <- manifestDetails.repositoryType match {
                           case None                 => getTypeFromGithub(repository)
                           case Some(repositoryType) => Future.successful(repositoryType)
                         }
    } yield {
      logger.debug(s"Mapping repository (${repository.name}) as $repositoryType")
      buildGitRepository(repository, repositoryType, manifestDetails.digitalServiceName, manifestDetails.owningTeams)
    }

  private def mapRepository(
    team          : GhTeam,
    repository    : GhRepository,
    persistedTeams: Seq[TeamRepositories]
    ): Future[GitRepository] = {
    val optPersistedRepository =
      persistedTeams
        .find(tr => tr.repositories.exists(r => r.name == repository.name && team.name == tr.teamName))
        .flatMap(_.repositories.find(_.url == repository.htmlUrl))

    optPersistedRepository match {
      case Some(persistedRepository) if repository.lastActiveDate == persistedRepository.lastActiveDate =>
        logger.info(s"Team '${team.name}' - Repository '${repository.htmlUrl}' already up to date")
        Future.successful(buildGitRepositoryUsingPreviouslyPersistedOne(repository, persistedRepository))
      case Some(persistedRepository) if repositoriesToIgnore.contains(persistedRepository.name) =>
        logger.info(s"Team '${team.name}' - Partial reload of ${repository.htmlUrl}")
        logger.debug(s"Mapping repository (${repository.name}) as ${RepoType.Other}")
        Future.successful(buildGitRepository(repository, RepoType.Other, None, persistedRepository.owningTeams))
      case Some(persistedRepository) =>
        logger.info(
          s"Team '${team.name}' - Full reload of ${repository.htmlUrl}: " +
            s"persisted repository last updated -> ${persistedRepository.lastActiveDate}, " +
            s"github repository last updated -> ${repository.lastActiveDate}")
        getRepositoryDetailsFromGithub(repository)
      case None =>
        logger.info(s"Team '${team.name}' - Full reload of ${repository.name} from github: never persisted before")
        getRepositoryDetailsFromGithub(repository)
    }
  }

  private def parseAppConfigFile(contents: String): Try[Object] =
    Try(new Yaml().load(contents))

  case class ManifestDetails(
    repositoryType: Option[RepoType],
    digitalServiceName: Option[String],
    owningTeams: Seq[String])

  private def parseManifest(repoName: String, manifest: String): Option[ManifestDetails] = {
    import scala.collection.JavaConverters._

    parseAppConfigFile(manifest) match {
      case Failure(exception) =>
        logger.warn(
          s"repository.yaml for $repoName is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
        None

      case Success(yamlMap) =>
        val config = yamlMap.asInstanceOf[java.util.Map[String, Object]].asScala

        val manifestDetails =
          ManifestDetails(
            repositoryType     = config.getOrElse("type", "").asInstanceOf[String].toLowerCase match {
                                   case "service" => Some(RepoType.Service)
                                   case "library" => Some(RepoType.Library)
                                   case _         => None
                                 },
            digitalServiceName = config.get("digital-service").map(_.toString),
            owningTeams        = try {
                                   config
                                     .getOrElse("owning-teams", new java.util.ArrayList[String])
                                     .asInstanceOf[java.util.List[String]]
                                     .asScala
                                     .toList
                                 } catch {
                                   case NonFatal(ex) =>
                                     logger.warn(
                                       s"Unable to get 'owning-teams' for repo '$repoName' from repository.yaml, problems were: ${ex.getMessage}")
                                     Nil
                                 }
          )

        logger.info(
          s"ManifestDetails for repo: $repoName is $manifestDetails, parsed from repository.yaml: $manifest"
        )

        Some(manifestDetails)
    }
  }

  private def getTypeFromGithub(repo: GhRepository): Future[RepoType] =
    if (repo.name.endsWith("-prototype")) {
      Future.successful(RepoType.Prototype)
    } else {
      isDeployable(repo).flatMap { deployable =>
        if (deployable) {
          Future.successful(RepoType.Service)
        } else {
          isReleasable(repo).map { releasable =>
            if (releasable) RepoType.Library else RepoType.Other
          }
        }
      }
    }

  private def isReleasable(repo: GhRepository): Future[Boolean] = {
    import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras._

    def hasSrcMainScala = exponentialRetry(retries, initialDuration)(hasPath(repo, "src/main/scala"))
    def hasSrcMainJava  = exponentialRetry(retries, initialDuration)(hasPath(repo, "src/main/java"))
    def containsTags    = hasTags(repo)

    (hasSrcMainScala || hasSrcMainJava) && containsTags
  }

  private def isDeployable(repo: GhRepository): Future[Boolean] = {
    import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras._

    def isPlayServiceF =
      exponentialRetry(retries, initialDuration)(hasFile(repo, "conf/application.conf"))

    def hasProcFileF =
      exponentialRetry(retries, initialDuration)(hasFile(repo, "Procfile"))

    def isJavaServiceF =
      exponentialRetry(retries, initialDuration)(hasFile(repo, "deploy.properties"))

    isPlayServiceF || isJavaServiceF || hasProcFileF
  }

  private def hasTags(repository: GhRepository): Future[Boolean] =
    withCounter(s"github.open.tags") {
      githubApiClient.getTags(HMRC_ORG, repository.name)
    }.map(_.nonEmpty)

  private def hasPath(repo: GhRepository, path: String): Future[Boolean] =
    withCounter(s"github.open.containsContent") {
      githubApiClient.repoContainsContent(path, repo.name, HMRC_ORG)
    }

  private def hasFile(repo: GhRepository, path: String): Future[Boolean] =
    githubConnector.getFileContent(repo.name, path).map(_.isDefined)

  def buildGitRepositoryUsingPreviouslyPersistedOne(repository: GhRepository, persistedRepository: GitRepository) =
    persistedRepository.copy(
      name           = repository.name,
      description    = repository.description,
      url            = repository.htmlUrl,
      createdDate    = repository.createdDate,
      lastActiveDate = repository.lastActiveDate,
      isPrivate      = repository.isPrivate
    )

  def buildGitRepository(
    repository: GhRepository,
    repositoryType: RepoType,
    maybeDigitalServiceName: Option[String],
    owningTeams: Seq[String]): GitRepository =
    GitRepository(
      repository.name,
      description        = repository.description,
      url                = repository.htmlUrl,
      createdDate        = repository.createdDate,
      lastActiveDate     = repository.lastActiveDate,
      isPrivate          = repository.isPrivate,
      repoType           = repositoryType,
      digitalServiceName = maybeDigitalServiceName,
      owningTeams        = owningTeams,
      language           = Option(repository.language)
    )

  def withCounter[T](name: String)(f: Future[T]) =
    f.andThen {
      case Success(_) =>
        defaultMetricsRegistry.counter(s"$name.success").inc()
      case Failure(_) =>
        defaultMetricsRegistry.counter(s"$name.failure").inc()
    }
}
