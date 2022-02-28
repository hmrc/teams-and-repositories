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
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.models.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persistence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.connectors.{GhTeam, GithubConnector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class Timestamper {
  def timestampF() = Instant.now()
}

@Singleton
case class PersistingService @Inject()(
  githubConfig    : GithubConfig,
  persister       : TeamsAndReposPersister,
  githubConnector : GithubConnector,
  timestamper     : Timestamper,
  configuration   : Configuration
) {
  private val logger = Logger(this.getClass)

  val sharedRepos: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  val dataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig    = githubConfig,
      githubConnector = githubConnector,
      timestampF      = timestamper.timestampF,
      sharedRepos     = sharedRepos
    )

  def persistTeamRepoMapping(implicit ec: ExecutionContext): Future[List[GhTeam]] =
    (for {
      teams                   <- dataSource.getTeams()
      reposByName             <- dataSource.getAllRepositoriesByName()
      repoNamesWithTeams      <- teams.foldLeftM(Set.empty[String]) { case (acc, team) =>
                                   for {
                                     repos     <- dataSource.getTeamRepositories(team, reposByName)
                                     _         <- persister.update(repos)
                                     repoNames =  repos.repositories.map(_.name).toSet
                                   } yield acc ++ repoNames
                                 }
      reposWithoutTeams       =  (reposByName -- repoNamesWithTeams).values.toList.sortBy(_.name)
      _                       <- persister.update(TeamRepositories.unknown(reposWithoutTeams, timestamper.timestampF()))
    } yield teams).recoverWith {
      case NonFatal(ex) =>
        logger.error("Could not persist to teams repo.", ex)
        Future.failed(ex)
    }

  def removeOrphanTeamsFromMongo(
    teamsFromGithub: List[GhTeam]
    )( implicit ec: ExecutionContext
    ): Future[Set[String]] =
    (for {
       mongoTeams      <- persister.getAllTeamsAndRepos(None).map(_.map(_.teamName).toSet)
       teamNamesFromGh =  teamsFromGithub.map(_.name)
       orphanTeams     =  (mongoTeams.filterNot(teamNamesFromGh.toSet) - TeamRepositories.TEAM_UNKNOWN)
       _               =  logger.info(s"Removing these orphan teams:[$orphanTeams]")
       deleted         <- persister.deleteTeams(orphanTeams)
     } yield deleted
    ).recover {
      case e =>
        logger.error("Could not remove orphan teams from mongo.", e)
        throw e
    }
}
