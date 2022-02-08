/*
 * Copyright 2022 HM Revenue & Customs
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
import org.yaml.snakeyaml.Yaml
import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


class GithubV3RepositoryDataSource(
  githubConfig    : GithubConfig,
  githubConnector : GithubConnector,
  timestampF      : () => Instant,
  sharedRepos     : List[String]
) {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  private val logger = Logger(this.getClass)


  def getTeams(): Future[List[GhTeam]] =
    githubConnector.getTeams()
      .map(_.filterNot(team => githubConfig.hiddenTeams.contains(team.name)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve teams for organisation list.", ex)
          Future.failed(ex)
      }

  def mapTeam(team: GhTeam, persistedTeams: Seq[TeamRepositories], updatedRepos: Seq[GitRepository]): Future[TeamRepositories] = {
    logger.debug(s"Mapping team (${team.name})")
    for {
      ghRepos            <- githubConnector.getReposForTeam(team)
      currentCreatedDate =  persistedTeams.find(_.teamName == team.name).flatMap(_.createdDate)
      optCreatedDate     <- if (currentCreatedDate.isEmpty)
                              githubConnector.getTeamDetail(team).map(_.map(_.createdDate))
                            else
                              Future.successful(currentCreatedDate)
      repos              =  ghRepos
                              .filterNot(repo => githubConfig.hiddenRepositories.contains(repo.name))
                              .map(repo => updatedRepos.find(_.name == repo.name).getOrElse(mapRepository(repo)))
    } yield
      TeamRepositories(
        teamName     = team.name,
        repositories = repos,
        createdDate  = optCreatedDate,
        updateDate   = timestampF()
      )
  }.recover {
    case e =>
      logger.error("Could not map teams with organisations.", e)
      throw e
  }

  def getAllRepositories(): Future[List[GitRepository]] =
    githubConnector.getRepos()
      .map(_.map(repo => buildGitRepository(repo, RepoType.Other, None, Seq.empty)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve repo list for organisation.", ex)
          Future.failed(ex)
      }

  private def mapRepository(repo: GhRepository): GitRepository = {
      val manifestDetails =
        repo
          .repoTypeHeuristics
          .repositoryYamlText
          .flatMap(parseManifest(repo.name, _))
          .getOrElse(ManifestDetails(None, None, Nil))

      val repoType =
        manifestDetails
          .repoType
          .getOrElse(RepoType.inferFromGhRepository(repo))

      buildGitRepository(repo, repoType, manifestDetails.digitalServiceName, manifestDetails.owningTeams)
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
      lastActiveDate     = repo.pushedAt,
      isPrivate          = repo.isPrivate,
      repoType           = repoType,
      digitalServiceName = digitalServiceName,
      owningTeams        = owningTeams,
      language           = repo.language,
      isArchived         = repo.isArchived,
      defaultBranch      = repo.defaultBranch,
      branchProtection   = repo.branchProtection
    )
}
