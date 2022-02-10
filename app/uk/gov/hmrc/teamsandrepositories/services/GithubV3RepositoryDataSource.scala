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
import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.{GitRepository, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{GhTeam, GithubConnector}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NonFatal


class GithubV3RepositoryDataSource(
  githubConfig    : GithubConfig,
  githubConnector : GithubConnector,
  timestampF      : () => Instant,
  sharedRepos     : List[String]
) {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  private val logger = Logger(this.getClass)

  def getTeams(): Future[List[GhTeam]] = {
    val notHidden: String => Boolean =
      !githubConfig.hiddenTeams.toSet.contains(_)

    githubConnector
      .getTeams()
      .map(_.filter(team => notHidden(team.name)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve teams for organisation list.", ex)
          Future.failed(ex)
      }
  }

  def getTeamRepositories(
    team: GhTeam,
    cache: Map[String, GitRepository] = Map.empty
  ): Future[TeamRepositories] = {
    logger.info(s"Fetching TeamRepositories for team: ${team.name}")

    val notHidden: String => Boolean =
      !githubConfig.hiddenRepositories.toSet.contains(_)

    for {
      ghRepos <- githubConnector.getReposForTeam(team)
      repos   =  ghRepos.collect { case r if notHidden(r.name) => cache.getOrElse(r.name, r.toGitRepository) }
    } yield
        TeamRepositories(
          teamName     = team.name,
          repositories = repos.sortBy(_.name),
          createdDate  = Some(team.createdAt),
          updateDate   = timestampF()
        )
  }.recover {
    case e =>
      logger.error(s"Could not fetch TeamRepositories for team: ${team.name}")
      throw e
  }

  def getAllRepositories(): Future[List[GitRepository]] = {
    val notHidden: String => Boolean =
      !githubConfig.hiddenRepositories.toSet.contains(_)

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

  def getAllRepositoriesByName(): Future[Map[String, GitRepository]] =
    getAllRepositories()
      .map(_.map(r => r.name -> r).toMap)

}
