/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.util.concurrent.Executors

import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.githubclient.{GhTeam, GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services._
import uk.gov.hmrc.teamsandrepositories.util.DateTimeUtils.millisToLocalDateTime

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class PersistingServiceSpec
    extends AnyWordSpec
    with MockitoSugar
    with ScalaFutures
    with Matchers
    with DefaultPatienceConfig {

  val now = LocalDateTime.now()

  val mockMetrics  = mock[Metrics]
  val mockRegistry = mock[MetricRegistry]
  val mockCounter  = mock[Counter]

  when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
  when(mockRegistry.counter(any())).thenReturn(mockCounter)

  val testTimestamper = new Timestamper {
    override def timestampF() = now
  }

  import testTimestamper._

  "persistTeamRepoMapping_new" should {

    "persist teams and their repos" in {
      val teamARepositories =
        TeamRepositories(
          "teamA",
          List(
            GitRepository("repo1", "Some Description", "url1", now, now, language = Some("Scala"), archived = false),
            GitRepository("repo2", "Some Description", "url2", now, now, language = Some("Scala"), archived = false)
          ),
          timestampF()
        )

      val teamBRepositories =
        TeamRepositories(
          "teamB",
          List(
            GitRepository("repo3", "Some Description", "url3", now, now, language = Some("Scala"), archived = false),
            GitRepository("repo4", "Some Description", "url4", now, now, language = Some("Scala"), archived = false)
          ),
          timestampF()
        )

      val reposWithoutTeams =
        List(
          GitRepository("repo5", "Some Description", "url5", now, now, language = Some("Scala"), archived = false),
          GitRepository("repo6", "Some Description", "url6", now, now, language = Some("Scala"), archived = false)
        )

      val dataSource = mock[GithubV3RepositoryDataSource]

      val ghTeamA = GhTeam("teamA", 1)
      val ghTeamB = GhTeam("teamB", 2)

      when(dataSource.getTeamsForHmrcOrg).thenReturn(Future.successful(List(ghTeamA, ghTeamB)))
      when(dataSource.mapTeam(eqTo(ghTeamA), any()))
        .thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(eqTo(ghTeamB), any()))
        .thenReturn(Future.successful(teamBRepositories))

      when(dataSource.getAllRepositories()).thenReturn(Future(reposWithoutTeams))

      val persistingService = buildPersistingService(dataSource, Nil, mockMetrics)

      persistingService.persistTeamRepoMapping.futureValue

      verify(dataSource).mapTeam(eqTo(ghTeamA), any())
      verify(dataSource).mapTeam(eqTo(ghTeamB), any())
      verify(persistingService.persister).update(teamARepositories)
      verify(persistingService.persister).update(teamBRepositories)
      verify(persistingService.persister)
        .update(TeamRepositories(TeamRepositories.TEAM_UNKNOWN, reposWithoutTeams, now))
    }

    "persist a team's repositories sorted alphabetically by name" in {
      val teamARepositoriesInDataSource1 =
        TeamRepositories(
          "teamA",
          List(
            GitRepository("repoB2", "Some Description", "urlB2", now, now, language = Some("Scala"), archived = false),
            GitRepository("repoA1", "Some Description", "urlA1", now, now, language = Some("Scala"), archived = false)
          ),
          timestampF()
        )

      val dataSource1ReposWithoutTeams =
        List(GitRepository("repo6", "Some Description", "url6", now, now, language = Some("Scala"), archived = false))

      val unknownTeamRepositories =
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = dataSource1ReposWithoutTeams,
          updateDate   = now
        )

      val dataSource = mock[GithubV3RepositoryDataSource]

      val ghTeamA = GhTeam("teamA", 1)

      when(dataSource.getTeamsForHmrcOrg).thenReturn(Future.successful(List(ghTeamA)))

      when(dataSource.mapTeam(eqTo(ghTeamA), any()))
        .thenReturn(Future.successful(teamARepositoriesInDataSource1))

      when(dataSource.getAllRepositories()).thenReturn(Future(dataSource1ReposWithoutTeams))

      val persistingService = buildPersistingService(dataSource, Nil, mockMetrics)

      persistingService.persistTeamRepoMapping.futureValue

      verify(dataSource).mapTeam(eqTo(ghTeamA), any())

      val mergedRepositories = teamARepositoriesInDataSource1.repositories.sortBy(_.name)
      verify(persistingService.persister).update(
        teamARepositoriesInDataSource1.copy(repositories = mergedRepositories)
      )
      verify(persistingService.persister).update(unknownTeamRepositories)
    }

    "process teams in the correct order so that the latest updated teams are processed last and teams that have not been processed are first" in {
      implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

      def buildTeamRepositories(teamName: String, repoName: String, url: String) =
        TeamRepositories(
          teamName,
          List(GitRepository(repoName, "Some Description", url, now, now, language = Some("Scala"), archived = false)),
          timestampF())

      val teamARepositories = buildTeamRepositories("teamA", "repo1", "url1")
      val teamBRepositories = buildTeamRepositories("teamB", "repo2", "url2")
      val teamCRepositories = buildTeamRepositories("teamC", "repo3", "url3")
      val teamDRepositories = buildTeamRepositories("teamD", "repo4", "url4")

      val dataSource = mock[GithubV3RepositoryDataSource]

      val ghTeamA = GhTeam("teamA", 1)
      val ghTeamB = GhTeam("teamB", 2)
      val ghTeamC = GhTeam("teamC", 3)
      val ghTeamD = GhTeam("teamD", 4)

      val reposWithoutTeams =
        List(GitRepository("repo5", "Some Description", "url5", now, now, language = Some("Scala"), archived = false))

      val unknownTeamRepositories =
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = reposWithoutTeams,
          updateDate   = now
        )

      when(dataSource.getTeamsForHmrcOrg)
        .thenReturn(Future.successful(List(ghTeamA, ghTeamB, ghTeamC, ghTeamD)))

      when(dataSource.mapTeam(eqTo(ghTeamA), any())).thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(eqTo(ghTeamB), any())).thenReturn(Future.successful(teamBRepositories))
      when(dataSource.mapTeam(eqTo(ghTeamC), any())).thenReturn(Future.successful(teamCRepositories))
      when(dataSource.mapTeam(eqTo(ghTeamD), any())).thenReturn(Future.successful(teamDRepositories))

      when(dataSource.getAllRepositories()).thenReturn(Future(reposWithoutTeams))

      // N.B teamD has not been processed (does not exist in db)
      val persistedRepositoriesForOrdering = Seq(
        TeamRepositories("teamA", Nil, updateDate = millisToLocalDateTime(1)),
        TeamRepositories("teamC", Nil, updateDate = millisToLocalDateTime(2)),
        TeamRepositories("teamB", Nil, updateDate = millisToLocalDateTime(3))
      )

      val persistingService =
        buildPersistingService(dataSource, persistedRepositoriesForOrdering, mockMetrics)

      val mappingTeamsOrder = inOrder(dataSource)
      val persistenceOrder  = inOrder(persistingService.persister)

      persistingService.persistTeamRepoMapping.futureValue

      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghTeamD), any())
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghTeamA), any())
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghTeamC), any())
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghTeamB), any())

      persistenceOrder.verify(persistingService.persister).update(teamDRepositories)
      persistenceOrder.verify(persistingService.persister).update(teamARepositories)
      persistenceOrder.verify(persistingService.persister).update(teamCRepositories)
      persistenceOrder.verify(persistingService.persister).update(teamBRepositories)
      persistenceOrder.verify(persistingService.persister).update(unknownTeamRepositories)
    }
  }

  "removeOrphanTeamsFromMongo" should {

    "should remove deleted teams" in {
      val dataSource1 = mock[GithubV3RepositoryDataSource]

      val persistingService = buildPersistingService(dataSource1, Nil, mockMetrics)

      val teamRepositoriesInMongo = Seq(
        TeamRepositories("team-a", Nil, LocalDateTime.now()),
        TeamRepositories("team-b", Nil, LocalDateTime.now()),
        TeamRepositories("team-c", Nil, LocalDateTime.now()),
        TeamRepositories("team-d", Nil, LocalDateTime.now())
      )

      when(persistingService.persister.getAllTeamsAndRepos(any()))
        .thenReturn(Future.successful(teamRepositoriesInMongo))
      when(persistingService.persister.deleteTeams(any())(any()))
        .thenReturn(Future.successful(Set("something not important")))

      persistingService.removeOrphanTeamsFromMongo(
        Seq(
          TeamRepositories("team-a", Nil, LocalDateTime.now()),
          TeamRepositories("team-c", Nil, LocalDateTime.now())
        )
      )(scala.concurrent.ExecutionContext.global)

      verify(persistingService.persister, timeout(1000)).deleteTeams(Set("team-b", "team-d"))
    }
  }

  private def buildPersistingService(
    mockedDataSource      : GithubV3RepositoryDataSource,
    storedTeamRepositories: Seq[TeamRepositories],
    metrics               : Metrics
  ) = {

    val mockGithubConfig          = mock[GithubConfig]
    val mockPersister             = mock[TeamsAndReposPersister]
    val mockGithubConnector       = mock[GithubConnector]
    val mockGithubClientDecorator = mock[GithubApiClientDecorator]
    val mockGithubApiClient       = mock[GithubApiClient]
    val gitApiConfig              = GitApiConfig(user = "", key = "open.key", apiUrl = "open.com")

    when(mockGithubConfig.githubApiOpenConfig)
      .thenReturn(gitApiConfig)

    when(mockGithubClientDecorator.githubApiClient(gitApiConfig.apiUrl, gitApiConfig.key))
      .thenReturn(mockGithubApiClient)

    when(mockPersister.getAllTeamsAndRepos(any()))
      .thenReturn(Future.successful(storedTeamRepositories))

    when(mockPersister.update(any())(any()))
      .thenAnswer { arg: TeamRepositories => Future.successful(arg) }


    new PersistingService(
      githubConfig             = mockGithubConfig,
      persister                = mockPersister,
      githubConnector          = mockGithubConnector,
      githubApiClientDecorator = mockGithubClientDecorator,
      timestamper              = testTimestamper,
      metrics                  = metrics,
      configuration            = Configuration(),
      futureHelpers            = new FutureHelpers(metrics)
      ) {
        override val dataSource: GithubV3RepositoryDataSource = mockedDataSource
      }
  }
}
