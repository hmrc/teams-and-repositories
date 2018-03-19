/*
 * Copyright 2016 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import java.util
import org.eclipse.egit.github.core.Repository
import org.yaml.snakeyaml.Yaml
import play.api.Logger
import play.api.libs.json._
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.githubclient._
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.helpers.RetryStrategy._
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

case class TeamNamesTuple(ghNames: Option[Future[Set[String]]] = None, mongoNames: Option[Future[Set[String]]] = None)

case class TeamAndOrgAndDataSource(organisation: GhOrganisation, team: GhTeam, dataSource: GithubV3RepositoryDataSource)

case class OneTeamAndItsDataSources(
  teamName: String,
  teamAndDataSources: Seq[TeamAndOrgAndDataSource],
  updateDate: Long)

object OneTeamAndItsDataSources {
  def apply(teamAndDataSources: Seq[TeamAndOrgAndDataSource], updateDate: Long): OneTeamAndItsDataSources = {
    val uniqueTeamNames = teamAndDataSources.map(_.team.name).toSet
    require(uniqueTeamNames.size == 1, s"All records much belong to the same team! teams:($uniqueTeamNames)")
    new OneTeamAndItsDataSources(teamAndDataSources.head.team.name, teamAndDataSources, updateDate)
  }
}

class GithubV3RepositoryDataSource(
  githubConfig: GithubConfig,
  val gh: GithubApiClient,
  timestampF: () => Long,
  val defaultMetricsRegistry: MetricRegistry,
  repositoriesToIgnore: List[String]) {

  import uk.gov.hmrc.teamsandrepositories.controller.BlockingIOExecutionContext._

  implicit val repositoryFormats     = Json.format[GitRepository]
  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  val retries: Int            = 5
  val initialDuration: Double = 50

  def getTeamsWithOrgAndDataSourceDetails: Future[List[TeamAndOrgAndDataSource]] =
    withCounter(s"github.open.orgs") {
      gh.getOrganisations
    } flatMap { orgs =>
      Future
        .sequence(
          orgs.map(org =>
            withCounter(s"github.open.teams") {
              gh.getTeamsForOrganisation(org.login)
            } map (teams => (org, teams)))
        )
        .map(_.map {
          case (ghOrg, ghTeams) =>
            val nonHiddenGhTeams = ghTeams.filter(team => !githubConfig.hiddenTeams.contains(team.name))
            nonHiddenGhTeams.map(ghTeam => TeamAndOrgAndDataSource(ghOrg, ghTeam, this))

        })
        .map(_.flatten) recover {
        case e =>
          Logger.error("Could not retrieve teams for organisation list.", e)
          throw e
      }
    }

  def mapTeam(
    organisation: GhOrganisation,
    team: GhTeam,
    persistedTeams: Future[Seq[TeamRepositories]]): Future[TeamRepositories] = {
    Logger.debug(s"Mapping team (${team.name})")
    exponentialRetry(retries, initialDuration) {
      withCounter(s"github.open.repos") {
        gh.getReposForTeam(team.id)
      } flatMap { repos =>
        Future
          .sequence(for {
            repo <- repos if !githubConfig.hiddenRepositories.contains(repo.name)
          } yield {
            mapRepository(organisation, team, repo, persistedTeams)
          })
          .map { (repos: List[GitRepository]) =>
            TeamRepositories(team.name, repositories = repos, timestampF())
          }
      }
    } recover {
      case e =>
        Logger.error("Could not map teams with organisations.", e)
        throw e
    }
  }

  def getAllRepositories(implicit ec: ExecutionContext): Future[List[GitRepository]] =
    gh.getOrganisations.map { orgs =>
      orgs.flatMap { org =>
        gh.repositoryService
          .getOrgRepositories(org.login)
          .asScala
          .map(toGitRepository)
          .toList
      }
    }

  private def toGitRepository(r: Repository): GitRepository =
    GitRepository(
      name           = r.getName,
      description    = Option(r.getDescription).getOrElse(""),
      url            = r.getHtmlUrl,
      createdDate    = r.getCreatedAt.getTime,
      lastActiveDate = r.getPushedAt.getTime,
      isPrivate      = r.isPrivate,
      language       = Option(r.getLanguage)
    )

  def getRepositoryDetailsFromGithub(organisation: GhOrganisation, repository: GhRepository): Future[GitRepository] =
    for {
      manifest <- withCounter(s"github.open.fileContent") {
                   gh.getFileContent("repository.yaml", repository.name, organisation.login)
                 }
      maybeManifestDetails = getMaybeManifestDetails(repository.name, manifest)
      repositoryType <- identifyRepository(repository, organisation, maybeManifestDetails.flatMap(_.repositoryType))
      maybeDigitalServiceName = maybeManifestDetails.flatMap(_.digitalServiceName)
      owningTeams             = maybeManifestDetails.map(_.owningTeams).getOrElse(Nil)
    } yield {
      Logger.debug(s"Mapping repository (${repository.name}) as $repositoryType")
      buildGitRepository(repository, repositoryType, maybeDigitalServiceName, owningTeams)
    }

  private def mapRepository(
    organisation: GhOrganisation,
    team: GhTeam,
    repository: GhRepository,
    persistedTeamsF: Future[Seq[TeamRepositories]]): Future[GitRepository] = {
    val eventualMaybePersistedRepository = persistedTeamsF
      .map(_.find(tr => tr.repositories.exists(r => r.name == repository.name && team.name == tr.teamName)))
      .map(_.flatMap(_.repositories.find(_.url == repository.htmlUrl)))

    eventualMaybePersistedRepository.flatMap {
      case Some(persistedRepository) if repository.lastActiveDate == persistedRepository.lastActiveDate =>
        Logger.info(s"Team '${team.name}' - Repository '${repository.htmlUrl}' already up to date")
        Future.successful(buildGitRepositoryUsingPreviouslyPersistedOne(repository, persistedRepository))
      case Some(persistedRepository) if repositoriesToIgnore.contains(persistedRepository.name) =>
        Logger.info(s"Team '${team.name}' - Partial reload of ${repository.htmlUrl}")
        Logger.debug(s"Mapping repository (${repository.name}) as ${RepoType.Other}")
        Future.successful(buildGitRepository(repository, RepoType.Other, None, persistedRepository.owningTeams))
      case Some(persistedRepository) =>
        Logger.info(
          s"Team '${team.name}' - Full reload of ${repository.htmlUrl}: " +
            s"persisted repository last updated -> ${persistedRepository.lastActiveDate}, " +
            s"github repository last updated -> ${repository.lastActiveDate}")
        getRepositoryDetailsFromGithub(organisation, repository)
      case None =>
        Logger.info(s"Team '${team.name}' - Full reload of ${repository.name} from github: never persisted before")
        getRepositoryDetailsFromGithub(organisation, repository)
    }
  }

  private def identifyRepository(
    repository: GhRepository,
    organisation: GhOrganisation,
    maybeRepoType: Option[RepoType]): Future[RepoType] =
    maybeRepoType match {
      case None                 => getTypeFromGithub(repository, organisation)
      case Some(repositoryType) => Future.successful(repositoryType)
    }

  private def parseAppConfigFile(contents: String): Try[Object] =
    Try(new Yaml().load(contents))

  case class ManifestDetails(
    repositoryType: Option[RepoType],
    digitalServiceName: Option[String],
    owningTeams: Seq[String])

  private def getMaybeManifestDetails(repoName: String, manifest: Option[String]): Option[ManifestDetails] = {
    import scala.collection.JavaConverters._

    manifest.flatMap { contents =>
      parseAppConfigFile(contents) match {
        case Failure(exception) => {
          Logger.warn(
            s"repository.yaml for $repoName is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
          None
        }
        case Success(yamlMap) => {
          val config = yamlMap.asInstanceOf[java.util.Map[String, Object]].asScala

          val manifestDetails =
            ManifestDetails(
              config.getOrElse("type", "").asInstanceOf[String].toLowerCase match {
                case "service" => Some(RepoType.Service)
                case "library" => Some(RepoType.Library)
                case _         => None
              },
              config.get("digital-service").map(_.toString),
              try {
                config
                  .getOrElse("owning-teams", new util.ArrayList[String])
                  .asInstanceOf[java.util.List[String]]
                  .asScala
                  .toList
              } catch {
                case NonFatal(ex) =>
                  Logger.warn(
                    s"Unable to get 'owning-teams' for repo '$repoName' from repository.yaml, problems were: ${ex.getMessage}")
                  Nil
              }
            )

          Logger.info(
            s"ManifestDetails for repo: $repoName is $manifestDetails, parsed from repository.yaml: $contents"
          )

          Some(manifestDetails)
        }
      }
    }
  }

  private def getTypeFromGithub(repo: GhRepository, organisation: GhOrganisation): Future[RepoType] =
    if (repo.name.endsWith("-prototype")) {
      Future.successful(RepoType.Prototype)
    } else {
      isDeployable(repo, organisation) flatMap { deployable =>
        if (deployable) Future.successful(RepoType.Service)
        else
          isReleasable(repo, organisation).map { releasable =>
            if (releasable) RepoType.Library
            else RepoType.Other
          }
      }
    }

  private def isReleasable(repo: GhRepository, organisation: GhOrganisation): Future[Boolean] = {
    import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras._

    def hasSrcMainScala =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "src/main/scala"))

    def hasSrcMainJava =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "src/main/java"))

    def containsTags =
      hasTags(organisation, repo)

    (hasSrcMainScala || hasSrcMainJava) && containsTags
  }

  private def isDeployable(repo: GhRepository, organisation: GhOrganisation): Future[Boolean] = {
    import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras._

    def isPlayServiceF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "conf/application.conf"))

    def hasProcFileF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "Procfile"))

    def isJavaServiceF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "deploy.properties"))

    isPlayServiceF || isJavaServiceF || hasProcFileF
  }

  private def hasTags(organisation: GhOrganisation, repository: GhRepository) =
    withCounter(s"github.open.tags") {
      gh.getTags(organisation.login, repository.name)
    } map (_.nonEmpty)

  private def hasPath(organisation: GhOrganisation, repo: GhRepository, path: String) =
    withCounter(s"github.open.containsContent") {
      gh.repoContainsContent(path, repo.name, organisation.login)
    }

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
