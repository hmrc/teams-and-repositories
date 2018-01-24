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

import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import java.util.Date
import java.util.concurrent.Executors
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.githubclient.{GhOrganisation, GhTeam, GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.services._

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

  "buildDataSource" should {

    val githubConfig          = mock[GithubConfig]
    val persister             = mock[TeamsAndReposPersister]
    val connector             = mock[MongoConnector]
    val githubClientDecorator = mock[GithubApiClientDecorator]

    "should create the right CompositeRepositoryDataSource" in {

      val gitApiOpenConfig       = mock[GitApiConfig]
      val gitApiEnterpriseConfig = mock[GitApiConfig]

      when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
      when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

      val enterpriseUrl = "enterprise.com"
      val enterpriseKey = "enterprise.key"
      when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
      when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

      val openUrl = "open.com"
      val openKey = "open.key"
      when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
      when(gitApiOpenConfig.key).thenReturn(openKey)

      val enterpriseGithubClient = mock[GithubApiClient]
      val openGithubClient       = mock[GithubApiClient]
      when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
      when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

      val compositeRepositoryDataSource = new GitCompositeDataSource(
        githubConfig,
        persister,
        connector,
        githubClientDecorator,
        testTimestamper,
        mockMetrics)

      verify(gitApiOpenConfig).apiUrl
      verify(gitApiOpenConfig).key
      verify(gitApiEnterpriseConfig).apiUrl
      verify(gitApiEnterpriseConfig).key

      compositeRepositoryDataSource.dataSources.size shouldBe 2

      val enterpriseDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(0)
      enterpriseDataSource shouldBe compositeRepositoryDataSource.enterpriseTeamsRepositoryDataSource

      val openDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(1)
      openDataSource shouldBe compositeRepositoryDataSource.openTeamsRepositoryDataSource
    }
  }

  "persistTeamRepoMapping_new" should {

    "persist teams and their repos" in {
      import BlockingIOExecutionContext._

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

      val dataSource         = mock[GithubV3RepositoryDataSource]
      val noEffectDataSource = mock[GithubV3RepositoryDataSource]

      val ghOrganisation = GhOrganisation("hmrc", 123)
      val ghTeamA        = GhTeam("teamA", 1)
      val ghTeamB        = GhTeam("teamB", 2)

      when(dataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(
        Future.successful(
          List(
            TeamAndOrgAndDataSource(ghOrganisation, ghTeamA, dataSource),
            TeamAndOrgAndDataSource(ghOrganisation, ghTeamB, dataSource)
          )))
      when(noEffectDataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(Nil))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any(), eqTo(false)))
        .thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any(), eqTo(false)))
        .thenReturn(Future.successful(teamBRepositories))

      when(dataSource.getAllRepositories(any())).thenReturn(Future(reposWithoutTeams))
      when(noEffectDataSource.getAllRepositories(any())).thenReturn(Future(Nil))

      val compositeDataSource = buildCompositeDataSource(dataSource, noEffectDataSource, Nil, mockMetrics)

      compositeDataSource.persistTeamRepoMapping().futureValue

      verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any(), eqTo(false))
      verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any(), eqTo(false))
      verify(compositeDataSource.persister).update(teamARepositories)
      verify(compositeDataSource.persister).update(teamBRepositories)
      verify(compositeDataSource.persister)
        .update(TeamRepositories(TeamRepositories.TEAM_UNKNOWN, reposWithoutTeams, now))
    }

    "persist a team's repositories from all data sources (combine them) with repositories sorted alphabetically by name" in {
      import BlockingIOExecutionContext._

      val teamARepositoriesInDataSource1 =
        TeamRepositories(
          "teamA",
          List(
            GitRepository("repoB2", "Some Description", "urlB2", now, now, language = Some("Scala")),
            GitRepository("repoA1", "Some Description", "urlA1", now, now, language = Some("Scala"))
          ),
          timestampF()
        )

      val teamARepositoriesInDataSource2 =
        TeamRepositories(
          "teamA",
          List(
            GitRepository("repoD4", "Some Description", "url4", now, now, language = Some("Scala")),
            GitRepository("repoC3", "Some Description", "url3", now, now, language = Some("Scala"))
          ),
          timestampF()
        )

      val dataSource1ReposWithoutTeams =
        List(GitRepository("repo6", "Some Description", "url6", now, now, language = Some("Scala")))

      val dataSource2ReposWithoutTeams =
        List(GitRepository("repo6", "Some Description", "url6", now, now, language = Some("Scala")))

      val unknownTeamRepositories =
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = dataSource1ReposWithoutTeams ++ dataSource2ReposWithoutTeams,
          updateDate   = now
        )

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      val dataSource2 = mock[GithubV3RepositoryDataSource]

      val ghOrganisation1      = GhOrganisation("hmrc", 123)
      val ghTeamAInDataSource1 = GhTeam("teamA", 1)

      val ghOrganisation2      = GhOrganisation("hmrc", 456)
      val ghTeamAInDataSource2 = GhTeam("teamA", 2)

      when(dataSource1.getTeamsWithOrgAndDataSourceDetails).thenReturn(
        Future.successful(
          List(
            TeamAndOrgAndDataSource(ghOrganisation1, ghTeamAInDataSource1, dataSource1)
          )))
      when(dataSource2.getTeamsWithOrgAndDataSourceDetails).thenReturn(
        Future.successful(
          List(
            TeamAndOrgAndDataSource(ghOrganisation2, ghTeamAInDataSource2, dataSource2)
          )))

      when(dataSource1.mapTeam(eqTo(ghOrganisation1), eqTo(ghTeamAInDataSource1), any(), eqTo(false)))
        .thenReturn(Future.successful(teamARepositoriesInDataSource1))
      when(dataSource2.mapTeam(eqTo(ghOrganisation2), eqTo(ghTeamAInDataSource2), any(), eqTo(false)))
        .thenReturn(Future.successful(teamARepositoriesInDataSource2))

      when(dataSource1.getAllRepositories(any())).thenReturn(Future(dataSource1ReposWithoutTeams))
      when(dataSource2.getAllRepositories(any())).thenReturn(Future(dataSource2ReposWithoutTeams))

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2, Nil, mockMetrics)

      compositeDataSource.persistTeamRepoMapping().futureValue

      verify(dataSource1).mapTeam(eqTo(ghOrganisation1), eqTo(ghTeamAInDataSource1), any(), eqTo(false))
      verify(dataSource2).mapTeam(eqTo(ghOrganisation2), eqTo(ghTeamAInDataSource2), any(), eqTo(false))

      val mergedRepositories =
        (teamARepositoriesInDataSource1.repositories ++ teamARepositoriesInDataSource2.repositories).sortBy(_.name)
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

      val dataSource         = mock[GithubV3RepositoryDataSource]
      val noEffectDataSource = mock[GithubV3RepositoryDataSource]

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
      when(noEffectDataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(Nil))

      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any(), eqTo(false)))
        .thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any(), eqTo(false)))
        .thenReturn(Future.successful(teamBRepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamC), any(), eqTo(false)))
        .thenReturn(Future.successful(teamCRepositories))
      when(dataSource.mapTeam(eqTo(ghOrganisation), eqTo(ghTeamD), any(), eqTo(false)))
        .thenReturn(Future.successful(teamDRepositories))

      when(dataSource.getAllRepositories(any())).thenReturn(Future(reposWithoutTeams))
      when(noEffectDataSource.getAllRepositories(any())).thenReturn(Future(Nil))

      // N.B teamD has not been processed (does not exist in db)
      val persistedRepositoriesForOrdering = Seq(
        TeamRepositories("teamA", Nil, updateDate = 1),
        TeamRepositories("teamC", Nil, updateDate = 2),
        TeamRepositories("teamB", Nil, updateDate = 3))

      val compositeDataSource =
        buildCompositeDataSource(dataSource, noEffectDataSource, persistedRepositoriesForOrdering, mockMetrics)

      val mappingTeamsOrder = Mockito.inOrder(dataSource)
      val persistenceOrder  = Mockito.inOrder(compositeDataSource.persister)

      compositeDataSource.persistTeamRepoMapping().futureValue

      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamD), any(), eqTo(false))
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamA), any(), eqTo(false))
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamC), any(), eqTo(false))
      mappingTeamsOrder.verify(dataSource).mapTeam(eqTo(ghOrganisation), eqTo(ghTeamB), any(), eqTo(false))

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
      val dataSource2 = mock[GithubV3RepositoryDataSource]

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2, Nil, mockMetrics)

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

//  "XXXXX!@" should {
//    "should update the timestamp for teams" in {
//            val teamsList = List(
//              TeamRepositories("A", List(GitRepository("A_r", "Some Description", "url_A", now, now)), System.currentTimeMillis()),
//              TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now)), System.currentTimeMillis()),
//              TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now)), System.currentTimeMillis()))
//
//            val dataSource = mock[GithubV3RepositoryDataSource]
//            when(dataSource.getTeamRepoMapping).thenReturn(successful(teamsList))
//
//
//            val compositeDataSource = buildCompositeDataSource(List(dataSource))
//            val result = compositeDataSource.persistTeamRepoMapping_new.futureValue
//
//            verify(persister, Mockito.timeout(1000)).updateTimestamp(ArgumentMatchers.any())
//          }
//  }

  private def buildCompositeDataSource(
    dataSource1: GithubV3RepositoryDataSource,
    dataSource2: GithubV3RepositoryDataSource,
    storedTeamRepositories: Seq[TeamRepositories],
    metrics: Metrics) = {

    val githubConfig          = mock[GithubConfig]
    val persister             = mock[TeamsAndReposPersister]
    val connector             = mock[MongoConnector]
    val githubClientDecorator = mock[GithubApiClientDecorator]

    val gitApiOpenConfig       = mock[GitApiConfig]
    val gitApiEnterpriseConfig = mock[GitApiConfig]

    when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
    when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

    val enterpriseUrl = "enterprise.com"
    val enterpriseKey = "enterprise.key"
    when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
    when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

    val openUrl = "open.com"
    val openKey = "open.key"
    when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
    when(gitApiOpenConfig.key).thenReturn(openKey)

    val enterpriseGithubClient = mock[GithubApiClient]
    val openGithubClient       = mock[GithubApiClient]

    when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
    when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

    val repositories: Seq[TeamRepositories] = Seq(TeamRepositories("testTeam", Nil, timestampF()))
    when(persister.getAllTeamAndRepos).thenReturn(Future.successful(storedTeamRepositories))
    when(persister.update(any())).thenAnswer(new Answer[Future[TeamRepositories]] {
      override def answer(invocation: InvocationOnMock): Future[TeamRepositories] = {
        val args = invocation.getArguments()
        Future.successful(args(0).asInstanceOf[TeamRepositories])
      }
    })

    new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator, testTimestamper, metrics) {
      override val dataSources: List[GithubV3RepositoryDataSource] = List(dataSource1, dataSource2)
    }
  }
}
