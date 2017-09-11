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

import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.githubclient.{GhOrganisation, GhTeam, GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.services._

import scala.concurrent.{ExecutionContext, Future}



class CompositeRepositoryDataSourceSpec extends WordSpec with MockitoSugar with ScalaFutures with Matchers with DefaultPatienceConfig {

  val now = new Date().getTime

  val testTimestamper = new Timestamper {
    override def timestampF() = now
  }

  import testTimestamper._


  "persistTeamRepoMapping_new" should {

    "persist teams and their repos" in {
      import BlockingIOExecutionContext._

      val teamARepositories =
        TeamRepositories("teamA", List(
          GitRepository("repo1", "Some Description", "url1", now, now, updateDate = timestampF()),
          GitRepository("repo2", "Some Description", "url2", now, now, updateDate = timestampF())
        ), timestampF())

      val teamBRepositories =
        TeamRepositories("teamB", List(
          GitRepository("repo3", "Some Description", "url3", now, now, updateDate = timestampF()),
          GitRepository("repo4", "Some Description", "url4", now, now, updateDate = timestampF())
        ), timestampF())


      val dataSource = mock[GithubV3RepositoryDataSource]
      val noEffectDataSource = mock[GithubV3RepositoryDataSource]

      val ghOrganisation = GhOrganisation("hmrc", 123)
      val ghTeamA = GhTeam("teamA", 1)
      val ghTeamB = GhTeam("teamB", 2)

      when(dataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(List(
        TeamAndOrgAndDataSource(ghOrganisation, ghTeamA, dataSource),
        TeamAndOrgAndDataSource(ghOrganisation, ghTeamB, dataSource)
      )))
      when(noEffectDataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(Nil))
      when(dataSource.mapTeam(ghOrganisation, ghTeamA)).thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(ghOrganisation, ghTeamB)).thenReturn(Future.successful(teamBRepositories))

      val compositeDataSource = buildCompositeDataSource(dataSource, noEffectDataSource, Nil)

      compositeDataSource.persistTeamRepoMapping_new.futureValue

      verify(dataSource).mapTeam(ghOrganisation, ghTeamA)
      verify(dataSource).mapTeam(ghOrganisation, ghTeamB)
      verify(compositeDataSource.persister).update(teamARepositories)
      verify(compositeDataSource.persister).update(teamBRepositories)
    }

    "persist team's repos from all data sources with repositories sorted alphabetically" in {
      import BlockingIOExecutionContext._

      val teamARepositoriesInDataSource1 =
        TeamRepositories("teamA", List(
          GitRepository("repoB2", "Some Description", "urlB2", now, now, updateDate = timestampF()),
            GitRepository("repoA1", "Some Description", "urlA1", now, now, updateDate = timestampF())
        ), timestampF())

      val teamARepositoriesInDataSource2 =
        TeamRepositories("teamA", List(
          GitRepository("repoD4", "Some Description", "url4", now, now, updateDate = timestampF()),
          GitRepository("repoC3", "Some Description", "url3", now, now, updateDate = timestampF())
        ), timestampF())


      val dataSource1 = mock[GithubV3RepositoryDataSource]
      val dataSource2 = mock[GithubV3RepositoryDataSource]

      val ghOrganisation1 = GhOrganisation("hmrc", 123)
      val ghTeamAInDataSource1 = GhTeam("teamA", 1)

      val ghOrganisation2 = GhOrganisation("hmrc", 456)
      val ghTeamAInDataSource2 = GhTeam("teamA", 2)


      when(dataSource1.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(List(
        TeamAndOrgAndDataSource(ghOrganisation1, ghTeamAInDataSource1, dataSource1)
      )))
      when(dataSource2.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(List(
        TeamAndOrgAndDataSource(ghOrganisation2, ghTeamAInDataSource2, dataSource2)
      )))


      when(dataSource1.mapTeam(ghOrganisation1, ghTeamAInDataSource1)).thenReturn(Future.successful(teamARepositoriesInDataSource1))
      when(dataSource2.mapTeam(ghOrganisation2, ghTeamAInDataSource2)).thenReturn(Future.successful(teamARepositoriesInDataSource2))

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2, Nil)

      compositeDataSource.persistTeamRepoMapping_new.futureValue

      verify(dataSource1).mapTeam(ghOrganisation1, ghTeamAInDataSource1)
      verify(dataSource2).mapTeam(ghOrganisation2, ghTeamAInDataSource2)

      val mergedRepositories = (teamARepositoriesInDataSource1.repositories ++ teamARepositoriesInDataSource2.repositories).sortBy(_.name)
      verify(compositeDataSource.persister).update(
        teamARepositoriesInDataSource1.copy(repositories = mergedRepositories)
      )
    }

    "process teams in the correct order so that the latest updated teams are processed last and teams that have not been processed are first" in {
      implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

      def buildTeamRepositories(teamName: String, repoName: String, url: String) =
        TeamRepositories(teamName, List(GitRepository(repoName, "Some Description", url, now, now, updateDate = timestampF())), timestampF())

      val teamARepositories = buildTeamRepositories("teamA", "repo1", "url1")
      val teamBRepositories = buildTeamRepositories("teamB", "repo2", "url2")
      val teamCRepositories = buildTeamRepositories("teamC", "repo3", "url3")
      val teamDRepositories = buildTeamRepositories("teamD", "repo4", "url4")


      val dataSource = mock[GithubV3RepositoryDataSource]
      val noEffectDataSource = mock[GithubV3RepositoryDataSource]

      val ghOrganisation = GhOrganisation("hmrc", 123)
      val ghTeamA = GhTeam("teamA", 1)
      val ghTeamB = GhTeam("teamB", 2)
      val ghTeamC = GhTeam("teamC", 3)
      val ghTeamD = GhTeam("teamD", 4)

      when(dataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(List(
        TeamAndOrgAndDataSource(ghOrganisation, ghTeamA, dataSource),
        TeamAndOrgAndDataSource(ghOrganisation, ghTeamB, dataSource),
        TeamAndOrgAndDataSource(ghOrganisation, ghTeamC, dataSource),
        TeamAndOrgAndDataSource(ghOrganisation, ghTeamD, dataSource)
      )))
      when(noEffectDataSource.getTeamsWithOrgAndDataSourceDetails).thenReturn(Future.successful(Nil))

      when(dataSource.mapTeam(ghOrganisation, ghTeamA)).thenReturn(Future.successful(teamARepositories))
      when(dataSource.mapTeam(ghOrganisation, ghTeamB)).thenReturn(Future.successful(teamBRepositories))
      when(dataSource.mapTeam(ghOrganisation, ghTeamC)).thenReturn(Future.successful(teamCRepositories))
      when(dataSource.mapTeam(ghOrganisation, ghTeamD)).thenReturn(Future.successful(teamDRepositories))


      // N.B teamD has not been processed (does not exist in db)
      val persistedRepositoriesForOrdering = Seq(
        TeamRepositories("teamA", Nil, updateDate = 1),
        TeamRepositories("teamC", Nil, updateDate = 2),
        TeamRepositories("teamB", Nil, updateDate = 3))

      val compositeDataSource = buildCompositeDataSource(dataSource, noEffectDataSource, persistedRepositoriesForOrdering)

      val mappingTeamsOrder = Mockito.inOrder(dataSource)
      val persistenceOrder = Mockito.inOrder(compositeDataSource.persister)

      compositeDataSource.persistTeamRepoMapping_new.futureValue

      mappingTeamsOrder.verify(dataSource).mapTeam(ghOrganisation, ghTeamD)
      mappingTeamsOrder.verify(dataSource).mapTeam(ghOrganisation, ghTeamA)
      mappingTeamsOrder.verify(dataSource).mapTeam(ghOrganisation, ghTeamC)
      mappingTeamsOrder.verify(dataSource).mapTeam(ghOrganisation, ghTeamB)

      persistenceOrder.verify(compositeDataSource.persister).update(teamDRepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(teamARepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(teamCRepositories)
      persistenceOrder.verify(compositeDataSource.persister).update(teamBRepositories)
    }


  }


  private def buildCompositeDataSource(dataSource1: GithubV3RepositoryDataSource,
                                       dataSource2: GithubV3RepositoryDataSource,
                                       storedTeamRepositories: Seq[TeamRepositories]) = {

    val githubConfig = mock[GithubConfig]
    val persister = mock[TeamsAndReposPersister]
    val connector = mock[MongoConnector]
    val githubClientDecorator = mock[GithubApiClientDecorator]

    val gitApiOpenConfig = mock[GitApiConfig]
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
    val openGithubClient = mock[GithubApiClient]

    when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
    when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

    val repositories: Seq[TeamRepositories] = Seq(TeamRepositories("testTeam", Nil, timestampF()))
    when(persister.getAllTeams).thenReturn(Future.successful(storedTeamRepositories))
    when(persister.update(ArgumentMatchers.any())).thenAnswer(new Answer[Future[TeamRepositories]] {
      override def answer(invocation: InvocationOnMock): Future[TeamRepositories] = {
        val args = invocation.getArguments()
        Future.successful(args(0).asInstanceOf[TeamRepositories])
      }
    })
    when(persister.updateTimestamp(ArgumentMatchers.any())).thenReturn(Future.successful(true))

    new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator, testTimestamper) {
      override val dataSources: List[GithubV3RepositoryDataSource] = List(dataSource1, dataSource2)
    }
  }
}
