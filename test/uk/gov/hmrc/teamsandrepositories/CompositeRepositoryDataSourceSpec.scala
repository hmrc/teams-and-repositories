/*
 * Copyright 2016 HM Revenue & Customs
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

import java.util.Date
import java.util.concurrent.Executors

import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import uk.gov.hmrc.githubclient.{GhOrganisation, GhTeam, GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.services._

import scala.concurrent.{ExecutionContext, Future}

class CompositeRepositoryDataSourceSpec
    extends WordSpec
    with MockitoSugar
    with ScalaFutures
    with Matchers
    with DefaultPatienceConfig {

  val now = new Date().getTime

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
      import uk.gov.hmrc.teamsandrepositories.controller.BlockingIOExecutionContext._

      val teamARepositories =
        TeamRepositories(
          "teamA",
          List(
            GitRepository("repo1", "Some Description", "url1", now, now, language = Some("Scala")),
            GitRepository("repo2", "Some Description", "url2", now, now, language = Some("Scala"))
          ),
          timestampF()
        )

      val teamBRepositories =
        TeamRepositories(
          "teamB",
          List(
            GitRepository("repo3", "Some Description", "url3", now, now, language = Some("Scala")),
            GitRepository("repo4", "Some Description", "url4", now, now, language = Some("Scala"))
          ),
          timestampF()
        )

      val reposWithoutTeams =
        List(
          GitRepository("repo5", "Some Description", "url5", now, now, language = Some("Scala")),
          GitRepository("repo6", "Some Description", "url6", now, now, language = Some("Scala"))
        )

      val dataSource = mock[GithubV3RepositoryDataSource]

      val ghOrganisation = GhOrganisation("hmrc", 123)
      val ghTeamA        = GhTeam("teamA", 1)
      val ghTeamB        = GhTeam("teamB", 2)

      when(dataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(
        Future.successful(
          List(
            TeamAndOrgAndDataSource(ghOrganisation, ghTeamA, dataSource),
            TeamAndOrgAndDataSource(ghOrganisation, ghTeamB, dataSource)
          )))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any()))
        .thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any()))
        .thenReturn(Future.successful(teamBRepositories))

      when(dataSource.getAllRepositories(any())).thenReturn(Future(reposWithoutTeams))

      val compositeDataSource = buildCompositeDataSource(dataSource, Nil, mockMetrics)

      compositeDataSource.persistTeamRepoMapping.futureValue

      verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any())
      verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any())
      verify(compositeDataSource.persister).update(teamARepositories)
      verify(compositeDataSource.persister).update(teamBRepositories)
      verify(compositeDataSource.persister)
        .update(TeamRepositories(TeamRepositories.TEAM_UNKNOWN, reposWithoutTeams, now))
    }

    "persist a team's repositories sorted alphabetically by name" in {
      import uk.gov.hmrc.teamsandrepositories.controller.BlockingIOExecutionContext._

      val teamARepositoriesInDataSource1 =
        TeamRepositories(
          "teamA",
          List(
            GitRepository("repoB2", "Some Description", "urlB2", now, now, language = Some("Scala")),
            GitRepository("repoA1", "Some Description", "urlA1", now, now, language = Some("Scala"))
          ),
          timestampF()
        )

      val dataSource1ReposWithoutTeams =
        List(GitRepository("repo6", "Some Description", "url6", now, now, language = Some("Scala")))

      val unknownTeamRepositories =
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = dataSource1ReposWithoutTeams,
          updateDate   = now
        )

      val dataSource1 = mock[GithubV3RepositoryDataSource]

      val ghOrganisation1      = GhOrganisation("hmrc", 123)
      val ghTeamAInDataSource1 = GhTeam("teamA", 1)

      when(dataSource1.getTeamsWithOrgAndDataSourceDetails).thenReturn(
        Future.successful(
          List(
            TeamAndOrgAndDataSource(ghOrganisation1, ghTeamAInDataSource1, dataSource1)
          )))

      when(dataSource1.mapTeam(eqTo(ghOrganisation1), eqTo(ghTeamAInDataSource1), any()))
        .thenReturn(Future.successful(teamARepositoriesInDataSource1))

      when(dataSource1.getAllRepositories(any())).thenReturn(Future(dataSource1ReposWithoutTeams))

      val compositeDataSource = buildCompositeDataSource(dataSource1, Nil, mockMetrics)

      compositeDataSource.persistTeamRepoMapping.futureValue

      verify(dataSource1).mapTeam(eqTo(ghOrganisation1), eqTo(ghTeamAInDataSource1), any())

      val mergedRepositories = teamARepositoriesInDataSource1.repositories.sortBy(_.name)
      verify(compositeDataSource.persister).update(
        teamARepositoriesInDataSource1.copy(repositories = mergedRepositories)
      )
      verify(compositeDataSource.persister).update(unknownTeamRepositories)
    }

    "process teams in the correct order so that the latest updated teams are processed last and teams that have not been processed are first" in {
      implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

      def buildTeamRepositories(teamName: String, repoName: String, url: String) =
        TeamRepositories(
          teamName,
          List(GitRepository(repoName, "Some Description", url, now, now, language = Some("Scala"))),
          timestampF())

      val teamARepositories = buildTeamRepositories("teamA", "repo1", "url1")
      val teamBRepositories = buildTeamRepositories("teamB", "repo2", "url2")
      val teamCRepositories = buildTeamRepositories("teamC", "repo3", "url3")
      val teamDRepositories = buildTeamRepositories("teamD", "repo4", "url4")

      val dataSource = mock[GithubV3RepositoryDataSource]

      val ghOrganisation = GhOrganisation("hmrc", 123)
      val ghTeamA        = GhTeam("teamA", 1)
      val ghTeamB        = GhTeam("teamB", 2)
      val ghTeamC        = GhTeam("teamC", 3)
      val ghTeamD        = GhTeam("teamD", 4)

      val reposWithoutTeams =
        List(GitRepository("repo5", "Some Description", "url5", now, now, language = Some("Scala")))

      val unknownTeamRepositories =
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = reposWithoutTeams,
          updateDate   = now
        )

      when(dataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(
        Future.successful(List(
          TeamAndOrgAndDataSource(ghOrganisation, ghTeamA, dataSource),
          TeamAndOrgAndDataSource(ghOrganisation, ghTeamB, dataSource),
          TeamAndOrgAndDataSource(ghOrganisation, ghTeamC, dataSource),
          TeamAndOrgAndDataSource(ghOrganisation, ghTeamD, dataSource)
        )))

      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any()))
        .thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any()))
        .thenReturn(Future.successful(teamBRepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamC), any()))
        .thenReturn(Future.successful(teamCRepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamD), any()))
        .thenReturn(Future.successful(teamDRepositories))

      when(dataSource.getAllRepositories(any())).thenReturn(Future(reposWithoutTeams))

      // N.B teamD has not been processed (does not exist in db)
      val persistedRepositoriesForOrdering = Seq(
        TeamRepositories("teamA", Nil, updateDate = 1),
        TeamRepositories("teamC", Nil, updateDate = 2),
        TeamRepositories("teamB", Nil, updateDate = 3))

      val compositeDataSource =
        buildCompositeDataSource(dataSource, persistedRepositoriesForOrdering, mockMetrics)

      val mappingTeamsOrder = Mockito.inOrder(dataSource)
      val persistenceOrder  = Mockito.inOrder(compositeDataSource.persister)

      compositeDataSource.persistTeamRepoMapping.futureValue

      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamD), any())
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any())
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamC), any())
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any())

      persistenceOrder.verify(compositeDataSource.persister).update(teamDRepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(teamARepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(teamCRepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(teamBRepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(unknownTeamRepositories)
    }

  }

  "removeOrphanTeamsFromMongo" should {

    "should remove deleted teams" in {
      val dataSource1 = mock[GithubV3RepositoryDataSource]

      val compositeDataSource = buildCompositeDataSource(dataSource1, Nil, mockMetrics)

      val teamRepositoriesInMongo = Seq(
        TeamRepositories("team-a", Nil, System.currentTimeMillis()),
        TeamRepositories("team-b", Nil, System.currentTimeMillis()),
        TeamRepositories("team-c", Nil, System.currentTimeMillis()),
        TeamRepositories("team-d", Nil, System.currentTimeMillis())
      )

      when(compositeDataSource.persister.getAllTeamAndRepos).thenReturn(Future.successful(teamRepositoriesInMongo))
      when(compositeDataSource.persister.deleteTeams(any()))
        .thenReturn(Future.successful(Set("something not important")))

      compositeDataSource.removeOrphanTeamsFromMongo(
        Seq(
          TeamRepositories("team-a", Nil, System.currentTimeMillis()),
          TeamRepositories("team-c", Nil, System.currentTimeMillis())))(scala.concurrent.ExecutionContext.global)

      verify(compositeDataSource.persister, Mockito.timeout(1000)).deleteTeams(Set("team-b", "team-d"))
    }
  }

  private def buildCompositeDataSource(
    mockedDataSource: GithubV3RepositoryDataSource,
    storedTeamRepositories: Seq[TeamRepositories],
    metrics: Metrics) = {

    val githubConfig          = mock[GithubConfig]
    val persister             = mock[TeamsAndReposPersister]
    val connector             = mock[MongoConnector]
    val githubClientDecorator = mock[GithubApiClientDecorator]
    val gitApiOpenConfig      = mock[GitApiConfig]

    when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

    val openUrl = "open.com"
    val openKey = "open.key"
    when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
    when(gitApiOpenConfig.key).thenReturn(openKey)

    val openGithubClient = mock[GithubApiClient]

    when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

    when(persister.getAllTeamAndRepos).thenReturn(Future.successful(storedTeamRepositories))
    when(persister.update(any())).thenAnswer(new Answer[Future[TeamRepositories]] {
      override def answer(invocation: InvocationOnMock): Future[TeamRepositories] = {
        val args = invocation.getArguments()
        Future.successful(args(0).asInstanceOf[TeamRepositories])
      }
    })

    new GitCompositeDataSource(
      githubConfig,
      persister,
      connector,
      githubClientDecorator,
      testTimestamper,
      metrics,
      Configuration()) {
      override val dataSource: GithubV3RepositoryDataSource = mockedDataSource
    }
  }
}
