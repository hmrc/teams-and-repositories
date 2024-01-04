/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{GhTeam, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamRepositories}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class TimeStamper {
  def timestampF(): Instant = Instant.now()
}
@Singleton
class GithubV3RepositoryDataSource @Inject()(
  githubConfig   : GithubConfig,
  githubConnector: GithubConnector,
  timeStamper    : TimeStamper,
  configuration  : Configuration
) {
  private val logger = Logger(this.getClass)

  val sharedRepos: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  def getTeams()(implicit ec: ExecutionContext): Future[List[GhTeam]] = {
    def notHidden(team: GhTeam) =
      !githubConfig.hiddenTeams.contains(team.name)

    githubConnector
      .getTeams()
      .map(_.filter(team => notHidden(team)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve teams for organisation list.", ex)
          Future.failed(ex)
      }
  }

  def getTeams(repoName: String)(implicit ec: ExecutionContext): Future[List[String]] = {
    def notHidden(teamName: String) =
      !githubConfig.hiddenTeams.contains(teamName)

    githubConnector
      .getTeams(repoName)
      .map(_.filter(team => notHidden(team)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"Could not retrieve teams for repo: $repoName.", ex)
          Future.failed(ex)
      }
  }


  def getTeamRepositories(
    team : GhTeam,
    cache: Map[String, GitRepository] = Map.empty
  )(implicit
    ec: ExecutionContext
  ): Future[TeamRepositories] = {
    logger.info(s"Fetching TeamRepositories for team: ${team.name}")

    def notHidden(repoName: String) =
      !githubConfig.hiddenRepositories.contains(repoName)

    for {
      ghRepos <- githubConnector.getReposForTeam(team)
      repos   =  ghRepos.collect { case r if notHidden(r.name) => cache.getOrElse(r.name, r.toGitRepository) }
    } yield
        TeamRepositories(
          teamName     = team.name,
          repositories = repos.sortBy(_.name),
          createdDate  = Some(team.createdAt),
          updateDate   = timeStamper.timestampF()
        )
  }.recover {
    case e =>
      logger.error(s"Could not fetch TeamRepositories for team: ${team.name}")
      throw e
  }

  def getAllRepositories()(implicit ec: ExecutionContext): Future[List[GitRepository]] = {
    def notHidden(repoName: String) =
      !githubConfig.hiddenRepositories.contains(repoName)

    logger.info("Fetching all repositories from GitHub")

    githubConnector.getRepos()
      .map(_.collect { case repo if notHidden(repo.name) => repo.toGitRepository })
      .map { repos =>
        logger.info(s"Finished fetching all repositories from GitHub (total fetched: ${repos.size})")
        repos
      }
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve repo list for organisation.", ex)
          Future.failed(ex)
      }
  }

  def getRepo(repoName: String)(implicit ec: ExecutionContext): Future[Option[GitRepository]] = {
    def notHidden(repoName: String) =
      !githubConfig.hiddenRepositories.contains(repoName)

    logger.info(s"Fetching repo: $repoName from GitHub")

    githubConnector.getRepo(repoName)
      .map(_.filter(repo => notHidden(repo.name)))
      .map(_.map(_.toGitRepository))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"Could not retrieve repo: $repoName.", ex)
          Future.failed(ex)
      }
  }

  def getRepositoryYaml(repoName: String)(implicit ec: ExecutionContext): Future[Option[String]] =
    githubConnector.getRepo(repoName)
      .map(_.flatMap(_.repositoryYamlText))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"Unable to retrieve repository.yaml for repo: $repoName - ${ex.getMessage}", ex)
          Future.failed(ex)
      }


  def getAllRepositoriesByName()(implicit ec: ExecutionContext): Future[Map[String, GitRepository]] =
    getAllRepositories()
      .map(_.map(r => r.name -> r).toMap)
}
