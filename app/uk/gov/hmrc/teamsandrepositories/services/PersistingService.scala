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

import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.config.{GithubConfig}
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import java.time.Instant
import scala.concurrent.ExecutionContext

@Singleton
class Timestamper {
  def timestampF() = Instant.now()
}

@Singleton
case class PersistingService @Inject()(
  githubConfig    : GithubConfig,
  persister       : RepositoriesPersistence,
  githubConnector : GithubConnector,
  timestamper     : Timestamper,
  configuration   : Configuration,
) {
  private val logger = Logger(this.getClass)

  val sharedRepos: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  val dataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig    = githubConfig,
      githubConnector = githubConnector,
      timestampF      = timestamper.timestampF,
      sharedRepos     = sharedRepos,
      configuration   = configuration
    )

  def updateRepositories()(implicit ec: ExecutionContext) = {
    for {
      teams     <- dataSource.getTeams()
      teamRepos <- teams.foldLeftM(List.empty[TeamRepositories]) { case (acc, team) =>
        dataSource.getTeamRepositories(team).map(_ :: acc)
      }
      reposWithTeams = teamRepos.foldLeft(Map.empty[String, GitRepository]) { case (acc, trs) =>
        trs.repositories.foldLeft(acc) { case (acc, repo) =>
          val r = acc.getOrElse(repo.name, repo)
          acc + (r.name -> r.copy(teams = trs.teamName :: r.teams))
        }
      }
      allRepos      <- dataSource.getAllRepositoriesByName()
      orphanRepos    = (allRepos -- reposWithTeams.keys).values
      reposToPersist = reposWithTeams.values.toSeq ++ orphanRepos
      _              = logger.info(s"found ${reposToPersist.length} repos")
      count         <- persister.updateRepos(reposToPersist)
    } yield count
  }
}
