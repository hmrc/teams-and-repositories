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

  def getTeams(): Future[List[GhTeam]] =
    githubConnector.getTeams()
      .map(_.filterNot(team => githubConfig.hiddenTeams.contains(team.name)))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve teams for organisation list.", ex)
          Future.failed(ex)
      }

  def mapTeam(team: GhTeam, updatedRepos: Seq[GitRepository]): Future[TeamRepositories] = {
    logger.debug(s"Mapping team (${team.name})")
    for {
      ghRepos <- githubConnector.getReposForTeam(team)
      repos   =  ghRepos
                   .filterNot(repo => githubConfig.hiddenRepositories.contains(repo.name))
                   .map(repo => updatedRepos.find(_.name == repo.name).getOrElse(repo.toGitRepository))
    } yield
      TeamRepositories(
        teamName     = team.name,
        repositories = repos,
        createdDate  = Some(team.createdAt),
        updateDate   = timestampF()
      )
  }.recover {
    case e =>
      logger.error("Could not map teams with organisations.", e)
      throw e
  }

  def getAllRepositories(): Future[List[GitRepository]] =
    githubConnector.getRepos()
      .map(_.map(repo =>
        repo
          .copy(repositoryYamlText = None)
          .toGitRepository
      ))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve repo list for organisation.", ex)
          Future.failed(ex)
      }

}
