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
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.ManifestDetails
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector}

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

  def mapTeam(team: GhTeam, persistedTeams: Seq[TeamRepositories], updatedRepos: Seq[GitRepository]): Future[TeamRepositories] = {
    logger.debug(s"Mapping team (${team.name})")
    for {
      ghRepos            <- githubConnector.getReposForTeam(team)
      repos              =  ghRepos
                              .filterNot(repo => githubConfig.hiddenRepositories.contains(repo.name))
                              .map(repo => updatedRepos.find(_.name == repo.name).getOrElse(buildGitRepository(repo)))
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
        buildGitRepository(repo.copy(manifestDetails = ManifestDetails(Some(RepoType.Other), None, Nil)))
      ))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error("Could not retrieve repo list for organisation.", ex)
          Future.failed(ex)
      }

  private def buildGitRepository(repo: GhRepository): GitRepository = {
    val repoType =
      repo
        .manifestDetails
        .repoType
        .getOrElse(RepoType.inferFromGhRepository(repo))

    GitRepository(
      name               = repo.name,
      description        = repo.description.getOrElse(""),
      url                = repo.htmlUrl,
      createdDate        = repo.createdDate,
      lastActiveDate     = repo.pushedAt,
      isPrivate          = repo.isPrivate,
      repoType           = repoType,
      digitalServiceName = repo.manifestDetails.digitalServiceName,
      owningTeams        = repo.manifestDetails.owningTeams,
      language           = repo.language,
      isArchived         = repo.isArchived,
      defaultBranch      = repo.defaultBranch,
      branchProtection   = repo.branchProtection
    )
  }
}
