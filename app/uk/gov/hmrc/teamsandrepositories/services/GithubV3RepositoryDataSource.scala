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
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.models.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.config.{GithubConfig}
import uk.gov.hmrc.teamsandrepositories.connectors.{GhTeam, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.models.GitRepository

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.control.NonFatal


class GithubV3RepositoryDataSource(
  githubConfig                 : GithubConfig,
  githubConnector              : GithubConnector,
  timestampF                   : () => Instant,
  sharedRepos                  : List[String],
  configuration                : Configuration
) {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

  private val logger = Logger(this.getClass)
  private val urlTemplate = configuration.get[String]("url-templates.prototype")

  def getTeams(): Future[List[GhTeam]] = {
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

  def getTeamRepositories(
    team: GhTeam,
    cache: Map[String, GitRepository] = Map.empty
  ): Future[TeamRepositories] = {
    logger.info(s"Fetching TeamRepositories for team: ${team.name}")

    def notHidden(repoName: String) =
      !githubConfig.hiddenRepositories.contains(repoName)

    for {
      ghRepos <- githubConnector.getReposForTeam(team)
      repos   =  ghRepos.collect { case r if notHidden(r.name) => cache.getOrElse(r.name, r.toGitRepository(urlTemplate)) }
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
    def notHidden(repoName: String) =
      !githubConfig.hiddenRepositories.contains(repoName)

    logger.info("Fetching all repositories from GitHub")

    githubConnector.getRepos()
      .map(_.collect { case repo if notHidden(repo.name) => repo.toGitRepository(urlTemplate) })
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
