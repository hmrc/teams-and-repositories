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

package uk.gov.hmrc.teamsandservices.teams

import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.concurrent.Execution.Implicits._
import uk.gov.hmrc.teamsandservices.DefaultPatienceConfig
import uk.gov.hmrc.teamsandservices.github.{GithubConfig, GithubConfigProvider}
import uk.gov.hmrc.teamsandservices.teams.ViewModels.{Repository, TeamRepositories}
import uk.gov.hmrc.githubclient
import uk.gov.hmrc.githubclient.GithubApiClient

import scala.concurrent.{ExecutionContext, Future}

class GithubV3TeamsRepositoryDataSourceSpec extends WordSpec with ScalaFutures with Matchers with DefaultPatienceConfig with MockitoSugar with BeforeAndAfterEach {

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams = List("hidden_team1", "hidden_team2")

  "Github v3 Data Source" should {
    val githubClient = mock[GithubApiClient]
    val dataSource = createDataSource(githubClient)

    "Return a list of teams and repositories, filtering out forks" in {
      when(githubClient.getOrganisations).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1),githubclient.GhTeam("B", 2))))
      when(githubClient.getTeamsForOrganisation("DDCN")).thenReturn(Future.successful(List(githubclient.GhTeam("C", 3),githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"), githubclient.GhRepository("A_r2", 5, "url_A2", fork = true))))
      when(githubClient.getReposForTeam(2)).thenReturn(Future.successful(List(githubclient.GhRepository("B_r", 2, "url_B"))))
      when(githubClient.getReposForTeam(3)).thenReturn(Future.successful(List(githubclient.GhRepository("C_r", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D", fork = true))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A"))),
        TeamRepositories("B", List(Repository("B_r", "url_B"))),
        TeamRepositories("C", List(Repository("C_r", "url_C"))),
        TeamRepositories("D", List()))
    }

    "Set internal = true if the DataSource is marked as internal" in {
      val internalDataSource = new GithubV3TeamsRepositoryDataSource(githubClient, isInternal = true) with GithubConfigProvider {
        override def githubConfig: GithubConfig = new GithubConfig {
          override def hiddenRepositories: List[String] = testHiddenRepositories
          override def hiddenTeams: List[String] = testHiddenTeams
        }
      }

      when(githubClient.getOrganisations).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1),githubclient.GhTeam("B", 2))))
      when(githubClient.getTeamsForOrganisation("DDCN")).thenReturn(Future.successful(List(githubclient.GhTeam("C", 3),githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"), githubclient.GhRepository("A_r2", 5, "url_A2", fork = true))))
      when(githubClient.getReposForTeam(2)).thenReturn(Future.successful(List(githubclient.GhRepository("B_r", 2, "url_B"))))
      when(githubClient.getReposForTeam(3)).thenReturn(Future.successful(List(githubclient.GhRepository("C_r", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D", fork = true))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      internalDataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", isInternal = true))),
        TeamRepositories("B", List(Repository("B_r", "url_B", isInternal = true))),
        TeamRepositories("C", List(Repository("C_r", "url_C", isInternal = true))),
        TeamRepositories("D", List()))
    }

    "Filter out repositories according to the hidden config" in  {
      when(githubClient.getOrganisations).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")).thenReturn(Future.successful(List(githubclient.GhTeam("C", 3),githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)).thenReturn(Future.successful(List(githubclient.GhRepository("hidden_repo1", 1, "url_A"), githubclient.GhRepository("A_r2", 5, "url_A2"))))
      when(githubClient.getReposForTeam(3)).thenReturn(Future.successful(List(githubclient.GhRepository("hidden_repo2", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r2", "url_A2"))),
        TeamRepositories("C", List()),
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }

    "Filter out teams according to the hidden config" in {
     when(githubClient.getOrganisations).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")).thenReturn(Future.successful(List(githubclient.GhTeam("hidden_team1", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")).thenReturn(Future.successful(List(githubclient.GhTeam("hidden_team2", 3), githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(3)).thenReturn(Future.successful(List(githubclient.GhRepository("C_r", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }

    "Set deployable=true if the repository contains an app folder" in {
      when(githubClient.getOrganisations).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("app","A_r","HMRC")).thenReturn(Future.successful(true))
      when(githubClient.repoContainsContent("app","D_r","DDCN")).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", deployable = true))),
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }

    "Set deployable=true if the repository contains a Procfile" in {
      when(githubClient.getOrganisations).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("Procfile","A_r","HMRC")).thenReturn(Future.successful(true))
      when(githubClient.repoContainsContent("Procfile","B_r","HMRC")).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", deployable = true))),
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }
  }

  private def createDataSource(githubClient: GithubApiClient): GithubV3TeamsRepositoryDataSource with GithubConfigProvider {def githubConfig: GithubConfig} = {
    new GithubV3TeamsRepositoryDataSource(githubClient, isInternal = false) with GithubConfigProvider {
      override def githubConfig: GithubConfig = new GithubConfig {
        override def hiddenRepositories: List[String] = testHiddenRepositories

        override def hiddenTeams: List[String] = testHiddenTeams
      }
    }
  }
}
