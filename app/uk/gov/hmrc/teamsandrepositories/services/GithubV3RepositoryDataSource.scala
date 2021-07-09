/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.Instant
import java.util.concurrent.Executors

import cats.implicits._
import com.codahale.metrics.MetricRegistry
import org.yaml.snakeyaml.Yaml
import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.helpers.RetryStrategy._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


class GithubV3RepositoryDataSource(
  githubConfig              : GithubConfig,
  githubConnector           : GithubConnector,
  timestampF                : () => Instant,
  val defaultMetricsRegistry: MetricRegistry,
  repositoriesToIgnore      : List[String],
  futureHelpers             : FutureHelpers
) {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  private val logger = Logger(this.getClass)

  val retries: Int              = 5
  val initialDuration: Duration = 50.millis

  def getTeams(): Future[List[GhTeam]] =
    withCounter(s"github.open.teams") {
      githubConnector.getTeams()
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
        githubConnector.getReposForTeam(team)
      }.flatMap(
        _
          .filterNot(repo => githubConfig.hiddenRepositories.contains(repo.name))
          .traverse(repo => mapRepository(team, repo, persistedTeams))
          .map(repos => TeamRepositories(team.name, repositories = repos.toList, timestampF()))
      )
    }.recover {
      case e =>
        logger.error("Could not map teams with organisations.", e)
        throw e
    }
  }

  def getAllRepositories(): Future[List[GitRepository]] =
    withCounter(s"github.open.allRepos") {
      githubConnector.getRepos()
        .map(_.map(repo => buildGitRepository(repo, RepoType.Other, None, Seq.empty)))
    }.recoverWith {
      case NonFatal(ex) =>
        logger.error("Could not retrieve repo list for organisation.", ex)
        Future.failed(ex)
    }

  private def buildGitRepositoryFromGithub(repo: GhRepository): Future[GitRepository] =
    for {
      optManifest     <- githubConnector.getFileContent(repo, "repository.yaml")
      manifestDetails =  optManifest.flatMap(parseManifest(repo.name, _))
                           .getOrElse(ManifestDetails(None, None, Nil))
      repoType        <- manifestDetails.repoType match {
                           case None           => getTypeFromGithub(repo)
                           case Some(repoType) => Future.successful(repoType)
                         }
    } yield {
      logger.debug(s"Mapping repository (${repo.name}) as $repoType")
      buildGitRepository(repo, repoType, manifestDetails.digitalServiceName, manifestDetails.owningTeams)
    }

  private def mapRepository(
    team          : GhTeam,
    repo          : GhRepository,
    persistedTeams: Seq[TeamRepositories]
  ): Future[GitRepository] = {
    val optPersistedRepository =
      persistedTeams
        .find(tr => tr.repositories.exists(r => r.name == repo.name && team.name == tr.teamName))
        .flatMap(_.repositories.find(_.url == repo.htmlUrl))

    optPersistedRepository match {
      case Some(persistedRepository) if persistedRepository.lastActiveDate.toEpochMilli >= repo.lastActiveDate.toEpochMilli =>
        logger.info(s"Team '${team.name}' - Repository '${repo.htmlUrl}' already up to date")
        Future.successful(
          buildGitRepository(
            repo               = repo,
            repoType           = persistedRepository.repoType,
            digitalServiceName = persistedRepository.digitalServiceName,
            owningTeams        = persistedRepository.owningTeams
          )
        )
      case Some(persistedRepository) if repositoriesToIgnore.contains(persistedRepository.name) =>
        logger.info(s"Team '${team.name}' - Partial reload of ${repo.htmlUrl}")
        logger.debug(s"Mapping repository (${repo.name}) as ${RepoType.Other}")
        Future.successful(
          buildGitRepository(
            repo               = repo,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            owningTeams        = persistedRepository.owningTeams
          )
        )
      case Some(persistedRepository) =>
        logger.info(
          s"Team '${team.name}' - Full reload of ${repo.htmlUrl}: " +
            s"persisted repository last updated -> ${persistedRepository.lastActiveDate}, " +
            s"github repository last updated -> ${repo.lastActiveDate}"
        )
        buildGitRepositoryFromGithub(repo)
      case None =>
        logger.info(s"Team '${team.name}' - Full reload of ${repo.name} from github: never persisted before")
        buildGitRepositoryFromGithub(repo)
    }
  }

  private def parseAppConfigFile(contents: String): Try[java.util.Map[String, Object]] =
    Try(new Yaml().load[java.util.Map[String, Object]](contents))

  case class ManifestDetails(
    repoType          : Option[RepoType],
    digitalServiceName: Option[String],
    owningTeams       : Seq[String]
  )

  private def parseManifest(repoName: String, manifest: String): Option[ManifestDetails] = {
    import scala.collection.JavaConverters._

    parseAppConfigFile(manifest) match {
      case Failure(exception) =>
        logger.warn(
          s"repository.yaml for $repoName is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
        None

      case Success(yamlMap) =>
        val config = yamlMap.asScala

        val manifestDetails =
          ManifestDetails(
            repoType           = config.getOrElse("type", "").asInstanceOf[String].toLowerCase match {
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

    // doesn't work for multi-module
    // guess we don't look at build.sbt since test project are not libraries, and seem to use src/test rather than src/main
    def hasSrcMainScala = exponentialRetry(retries, initialDuration)(existsContent(repo, "src/main/scala"))
    def hasSrcMainJava  = exponentialRetry(retries, initialDuration)(existsContent(repo, "src/main/java"))
    def containsTags    = hasTags(repo)

    (hasSrcMainScala || hasSrcMainJava) && containsTags
  }

  private def isDeployable(repo: GhRepository): Future[Boolean] = {
    import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras._

    def isPlayServiceF =
      exponentialRetry(retries, initialDuration)(existsContent(repo, "conf/application.conf"))

    def hasProcFileF =
      exponentialRetry(retries, initialDuration)(existsContent(repo, "Procfile"))

    def isJavaServiceF =
      exponentialRetry(retries, initialDuration)(existsContent(repo, "deploy.properties"))

    isPlayServiceF || isJavaServiceF || hasProcFileF
  }

  private def hasTags(repo: GhRepository): Future[Boolean] =
    withCounter(s"github.open.tags") {
      githubConnector.hasTags(repo)
    }

  private def existsContent(repo: GhRepository, path: String): Future[Boolean] =
    withCounter(s"github.open.containsContent") {
      githubConnector.existsContent(repo, path)
    }

  private def buildGitRepository(
    repo             : GhRepository,
    repoType          : RepoType,
    digitalServiceName: Option[String],
    owningTeams       : Seq[String]
  ): GitRepository =
    GitRepository(
      name               = repo.name,
      description        = repo.description.getOrElse(""),
      url                = repo.htmlUrl,
      createdDate        = repo.createdDate,
      lastActiveDate     = repo.lastActiveDate,
      isPrivate          = repo.isPrivate,
      repoType           = repoType,
      digitalServiceName = digitalServiceName,
      owningTeams        = owningTeams,
      language           = repo.language,
      isArchived         = repo.isArchived,
      defaultBranch      = repo.defaultBranch
    )

  def withCounter[T](name: String)(f: Future[T]) =
    f.andThen {
      case Success(_) =>
        defaultMetricsRegistry.counter(s"$name.success").inc()
      case Failure(_) =>
        defaultMetricsRegistry.counter(s"$name.failure").inc()
    }
}
