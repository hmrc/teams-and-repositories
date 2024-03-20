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
import play.api.Logger
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
  githubConnector: GithubConnector,
  timeStamper    : TimeStamper
) {
  private val logger = Logger(this.getClass)

  def getTeams()(implicit ec: ExecutionContext): Future[List[GhTeam]] = {

    githubConnector
      .getTeams()
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve teams for organisation list.", ex)
          Future.failed(ex)
      }
  }

  def getTeams(repoName: String)(implicit ec: ExecutionContext): Future[List[String]] = {
    githubConnector
      .getTeams(repoName)
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

    for {
      ghRepos <- githubConnector.getReposForTeam(team)
      repos    = ghRepos.filter(_.permission == "WRITE")
                   .map(repo => cache.getOrElse(repo.ghRepository.name, repo.ghRepository.toGitRepository))
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

    logger.info("Fetching all repositories from GitHub")

    githubConnector.getRepos()
      .map(_.map(_.toGitRepository))
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
    logger.info(s"Fetching repo: $repoName from GitHub")

    githubConnector.getRepo(repoName)
      .map(_.map(_.toGitRepository))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"Could not retrieve repo: $repoName.", ex)
          Future.failed(ex)
      }
  }

  def getAllRepositoriesByName()(implicit ec: ExecutionContext): Future[Map[String, GitRepository]] =
    getAllRepositories()
      .map(_.map(r => r.name -> r).toMap)
}
