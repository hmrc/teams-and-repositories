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

import com.google.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import play.api.libs.json._
import uk.gov.hmrc.githubclient.{GhOrganisation, GhRepository, GhTeam, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.helpers.RetryStrategy._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class TeamNamesTuple(ghNames: Option[Future[Set[String]]] = None, mongoNames: Option[Future[Set[String]]] = None)

case class TeamAndOrgAndDataSource(organisation: GhOrganisation, team: GhTeam, dataSource: GithubV3RepositoryDataSource)

case class OneTeamAndItsDataSources(teamName: String, teamAndDataSources: Seq[TeamAndOrgAndDataSource], updateDate: Long)

object OneTeamAndItsDataSources {

  def apply(teamAndDataSources: Seq[TeamAndOrgAndDataSource], updateDate: Long): OneTeamAndItsDataSources = {
    val uniqueTeamNames = teamAndDataSources.map(_.team.name).toSet
    require(uniqueTeamNames.size == 1, s"All records much belong to the same team! teams:($uniqueTeamNames)")
    new OneTeamAndItsDataSources(teamAndDataSources.head.team.name, teamAndDataSources, updateDate)
  }

}


class GithubV3RepositoryDataSource(githubConfig: GithubConfig,
                                   val gh: GithubApiClient,
                                   val isInternal: Boolean,
                                   timestampF: () => Long) {


  lazy val logger = LoggerFactory.getLogger(this.getClass)

  //!@ use the play ec
  import uk.gov.hmrc.teamsandrepositories.BlockingIOExecutionContext._

  implicit val repositoryFormats = Json.format[GitRepository]

  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  val retries: Int = 5
  val initialDuration: Double = 50


  def getTeamsWithOrgAndDataSourceDetails: Future[List[TeamAndOrgAndDataSource]] = {
    gh.getOrganisations.flatMap { orgs =>
      Future.sequence(
        orgs.map(org => gh.getTeamsForOrganisation(org.login).map(teams => (org, teams)))
      ).map(_.map { case (ghOrg, ghTeams) =>
        val nonHiddenGhTeams = ghTeams.filter(team => !githubConfig.hiddenTeams.contains(team.name))
        nonHiddenGhTeams.map(ghTeam => TeamAndOrgAndDataSource(ghOrg, ghTeam, this))

      }).map(_.flatten) recover {
        case e =>
          logger.error("Could not retrieve teams for organisation list.", e)
          throw e
      }
    }
  }

  def mapTeam(organisation: GhOrganisation, team: GhTeam): Future[TeamRepositories] = {
    logger.debug(s"Mapping team (${team.name})")
    exponentialRetry(retries, initialDuration) {
      gh.getReposForTeam(team.id).flatMap { repos =>
        Future.sequence(for {
          repo <- repos; if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
        } yield mapRepository(organisation, repo)).map { (repos: List[GitRepository]) =>

          TeamRepositories(team.name, repositories = repos, timestampF())
        }
      }
    } recover {
      case e =>
        logger.error("Could not map teams with organisations.", e)
        throw e
    }
  }



  private def mapRepository(organisation: GhOrganisation, repository: GhRepository): Future[GitRepository] = {
    for {
      manifest <- gh.getFileContent("repository.yaml", repository.name, organisation.login)
      maybeManifestDetails = getMaybeManifestDetails(repository.name, manifest)
      repositoryType <- identifyRepository(repository, organisation, maybeManifestDetails.flatMap(_.repositoryType))

      maybeDigitalServiceName = maybeManifestDetails.flatMap(_.digitalServiceName)
    } yield {
      logger.debug(s"Mapping repository (${repository.name}) as $repositoryType")
      GitRepository(repository.name, repository.description, repository.htmlUrl, createdDate = repository.createdDate, lastActiveDate = repository.lastActiveDate, isInternal = this.isInternal, isPrivate = repository.isPrivate, repoType = repositoryType, digitalServiceName = maybeDigitalServiceName)
    }
  }

  private def identifyRepository(repository: GhRepository, organisation: GhOrganisation, maybeRepoType: Option[RepoType]): Future[RepoType] =
    maybeRepoType match {
      case None => getTypeFromGithub(repository, organisation)
      case Some(repositoryType) => Future.successful(repositoryType)
    }

  private def parseAppConfigFile(contents: String): Try[Object] =
    Try(new Yaml().load(contents))


  case class ManifestDetails(repositoryType: Option[RepoType], digitalServiceName: Option[String])

  private def getMaybeManifestDetails(repoName: String, manifest: Option[String]): Option[ManifestDetails] = {
    import scala.collection.JavaConverters._

    manifest.flatMap { contents =>
      parseAppConfigFile(contents) match {
        case Failure(exception) => {
          logger.warn(s"repository.yaml for $repoName is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
          None
        }
        case Success(yamlMap) => {
          val config = yamlMap.asInstanceOf[java.util.Map[String, Object]].asScala

          Some(ManifestDetails(
            config.getOrElse("type", "").asInstanceOf[String].toLowerCase match {
              case "service" => Some(RepoType.Service)
              case "library" => Some(RepoType.Library)
              case _ => None
            },
            config.get("digital-service").map(_.toString)
          ))
        }
      }
    }
  }


  private def getTypeFromGithub(repo: GhRepository, organisation: GhOrganisation): Future[RepoType] = {
    isPrototype(repo) flatMap { prototype =>
      if (prototype) {
        Future.successful(RepoType.Prototype)
      } else {
        isDeployable(repo, organisation) flatMap { deployable =>
          if (deployable) Future.successful(RepoType.Service)
          else isReleasable(repo, organisation).map { releasable =>
            if (releasable) RepoType.Library
            else RepoType.Other
          }
        }
      }
    }
  }

  private def isPrototype(repo: GhRepository): Future[Boolean] =
    Future.successful {
      repo.name.endsWith("-prototype")
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
    gh.getTags(organisation.login, repository.name).map(_.nonEmpty)

  private def hasPath(organisation: GhOrganisation, repo: GhRepository, path: String) =
    gh.repoContainsContent(path, repo.name, organisation.login)

}

