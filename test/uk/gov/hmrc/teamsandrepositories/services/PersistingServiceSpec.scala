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
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.teamsandrepositories.models._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{GhTeam, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.persistence.TeamsAndReposPersister

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class PersistingServiceSpec
  extends AnyWordSpec
     with MockitoSugar
     with ScalaFutures
     with Matchers
     with IntegrationPatience {

  val now = Instant.now()

  val testTimestamper = new Timestamper {
    override def timestampF() = now
  }

  import testTimestamper._

  val teamCreatedDate = Instant.parse("2019-04-01T12:00:00Z")

  "persistTeamRepoMapping" should {
    "persist teams and their repos" in {
      val teamARepositories =
        TeamRepositories(
          teamName     = "teamA",
          repositories = List(
            GitRepository(
              name             = "repo1",
              description      = "Some Description",
              url              = "url1",
              createdDate      = now,
              lastActiveDate   = now,
              language         = Some("Scala"),
              isArchived       = false,
              defaultBranch    = "main"
            ),
            GitRepository(
              name           = "repo2",
              description    = "Some Description",
              url            = "url2",
              createdDate    = now,
              lastActiveDate = now,
              language       = Some("Scala"),
              isArchived     = false,
              defaultBranch  = "main"
            )
          ),
          createdDate = Some(teamCreatedDate),
          updateDate  = timestampF()
        )

      val teamBRepositories =
        TeamRepositories(
          teamName     = "teamB",
          repositories = List(
            GitRepository(
              name           = "repo3",
              description    = "Some Description",
              url            = "url3",
              createdDate    = now,
              lastActiveDate = now,
              language       = Some("Scala"),
              isArchived     = false,
              defaultBranch  = "main"
            ),
            GitRepository(
              name           = "repo4",
              description    = "Some Description",
              url            = "url4",
              createdDate    = now,
              lastActiveDate = now,
              language       = Some("Scala"),
              isArchived     = false,
              defaultBranch  = "main"
            )
          ),
          createdDate = Some(teamCreatedDate),
          updateDate  = timestampF()
        )

      val reposWithoutTeams =
        List(
          GitRepository(
            name           = "repo5",
            description    = "Some Description",
            url            = "url5",
            createdDate    = now,
            lastActiveDate = now,
            language       = Some("Scala"),
            isArchived     = false,
            defaultBranch  = "main"
          ),
          GitRepository(
            name           = "repo6",
            description    = "Some Description",
            url            = "url6",
            createdDate    = now,
            lastActiveDate = now,
            language       = Some("Scala"),
            isArchived     = false,
            defaultBranch  = "main"
          )
        )

      val dataSource = mock[GithubV3RepositoryDataSource]

      val createdAt = Instant.now()

      val ghTeamA = GhTeam(name = "teamA", createdAt = createdAt)
      val ghTeamB = GhTeam(name = "teamB", createdAt = createdAt)

      when(dataSource.getTeams())
        .thenReturn(Future.successful(List(ghTeamA, ghTeamB)))

      when(dataSource.getTeamRepositories(eqTo(ghTeamA), any()))
        .thenReturn(Future.successful(teamARepositories))

      when(dataSource.getTeamRepositories(eqTo(ghTeamB), any()))
        .thenReturn(Future.successful(teamBRepositories))

      when(dataSource.getAllRepositoriesByName())
        .thenReturn(Future.successful(reposWithoutTeams.map(r => r.name -> r).toMap))

      val persistingService = buildPersistingService(dataSource, Nil)

      persistingService.persistTeamRepoMapping.futureValue

      verify(dataSource).getTeamRepositories(eqTo(ghTeamA), any())
      verify(dataSource).getTeamRepositories(eqTo(ghTeamB), any())
      verify(persistingService.persister).update(teamARepositories)
      verify(persistingService.persister).update(teamBRepositories)
      verify(persistingService.persister)
        .update(TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = reposWithoutTeams,
          createdDate  = None,
          updateDate   = now))
    }
  }

  "removeOrphanTeamsFromMongo" should {
    "should remove deleted teams" in {
      val dataSource1 = mock[GithubV3RepositoryDataSource]

      val persistingService = buildPersistingService(dataSource1, Nil)

      val teamRepositoriesInMongo = Seq(
        TeamRepositories(teamName = "team-a", repositories = Nil, createdDate = Some(teamCreatedDate), updateDate = Instant.now()),
        TeamRepositories(teamName = "team-b", repositories = Nil, createdDate = Some(teamCreatedDate), updateDate = Instant.now()),
        TeamRepositories(teamName = "team-c", repositories = Nil, createdDate = Some(teamCreatedDate), updateDate = Instant.now()),
        TeamRepositories(teamName = "team-d", repositories = Nil, createdDate = Some(teamCreatedDate), updateDate = Instant.now())
      )

      when(persistingService.persister.getAllTeamsAndRepos(any()))
        .thenReturn(Future.successful(teamRepositoriesInMongo))
      when(persistingService.persister.deleteTeams(any())(any()))
        .thenReturn(Future.successful(Set("something not important")))

      persistingService.removeOrphanTeamsFromMongo(
        List(
          GhTeam(name = "team-a", createdAt = teamCreatedDate),
          GhTeam(name = "team-c", createdAt = teamCreatedDate)
        )
      )(scala.concurrent.ExecutionContext.global)

      verify(persistingService.persister, timeout(1000)).deleteTeams(Set("team-b", "team-d"))
    }
  }

  private def buildPersistingService(
    mockedDataSource      : GithubV3RepositoryDataSource,
    storedTeamRepositories: Seq[TeamRepositories]
  ) = {

    val mockGithubConfig          = mock[GithubConfig]
    val mockPersister             = mock[TeamsAndReposPersister]
    val mockGithubConnector       = mock[GithubConnector]
    val configuration             = Configuration.from(Map("shared.repositories" -> Nil))

    when(mockGithubConfig.apiUrl)
      .thenReturn("open.com")
    when(mockGithubConfig.key)
      .thenReturn("open.key")

    when(mockPersister.getAllTeamsAndRepos(any()))
      .thenReturn(Future.successful(storedTeamRepositories))

    when(mockPersister.update(any())(any()))
      .thenAnswer { arg: TeamRepositories => Future.successful(arg) }


    new PersistingService(
      githubConfig    = mockGithubConfig,
      persister       = mockPersister,
      githubConnector = mockGithubConnector,
      timestamper     = testTimestamper,
      configuration   = configuration
    ) {
        override val dataSource: GithubV3RepositoryDataSource = mockedDataSource
      }
  }
}
