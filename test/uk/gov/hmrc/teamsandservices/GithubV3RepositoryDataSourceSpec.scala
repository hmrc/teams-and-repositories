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

package uk.gov.hmrc.teamsandservices

import org.mockito.Matchers._
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.gov.hmrc.githubclient
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandservices.RepoType._
import uk.gov.hmrc.teamsandservices.TeamRepositories
import uk.gov.hmrc.teamsandservices.config.{GithubConfig, GithubConfigProvider}

import scala.concurrent.{ExecutionContext, Future}

class GithubV3RepositoryDataSourceSpec extends WordSpec with ScalaFutures with Matchers with DefaultPatienceConfig with MockitoSugar with BeforeAndAfterEach {

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams = List("hidden_team1", "hidden_team2")

  "Github v3 Data Source" should {
    val githubClient = mock[GithubApiClient]
    val ec = BlockingIOExecutionContext.executionContext
    val dataSource = createDataSource(githubClient)

    "Return a list of teams and repositories, filtering out forks" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1),githubclient.GhTeam("B", 2))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("C", 3),githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"), githubclient.GhRepository("A_r2", 5, "url_A2", fork = true))))
      when(githubClient.getReposForTeam(2)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("B_r", 2, "url_B"))))
      when(githubClient.getReposForTeam(3)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("C_r", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D", fork = true))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))
      when(githubClient.getTags(anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(List.empty))


      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A"))),
        TeamRepositories("B", List(Repository("B_r", "url_B"))),
        TeamRepositories("C", List(Repository("C_r", "url_C"))),
        TeamRepositories("D", List()))
    }

    "Set internal = true if the DataSource is marked as internal" in {

      val internalDataSource = new GithubV3RepositoryDataSource(githubClient, isInternal = true) with GithubConfigProvider {
        override def githubConfig: GithubConfig = new GithubConfig {
          override def hiddenRepositories: List[String] = testHiddenRepositories
          override def hiddenTeams: List[String] = testHiddenTeams
        }
      }

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1),githubclient.GhTeam("B", 2))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("C", 3),githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"), githubclient.GhRepository("A_r2", 5, "url_A2", fork = true))))
      when(githubClient.getReposForTeam(2)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("B_r", 2, "url_B"))))
      when(githubClient.getReposForTeam(3)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("C_r", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D", fork = true))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      internalDataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", isInternal = true))),
        TeamRepositories("B", List(Repository("B_r", "url_B", isInternal = true))),
        TeamRepositories("C", List(Repository("C_r", "url_C", isInternal = true))),
        TeamRepositories("D", List()))
    }

    "Filter out repositories according to the hidden config" in  {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("C", 3),githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("hidden_repo1", 1, "url_A"), githubclient.GhRepository("A_r2", 5, "url_A2"))))
      when(githubClient.getReposForTeam(3)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("hidden_repo2", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r2", "url_A2"))),
        TeamRepositories("C", List()),
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }

    "Filter out teams according to the hidden config" in {

     when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("hidden_team1", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("hidden_team2", 3), githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(3)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("C_r", 3, "url_C"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }

    "Set repoType Service if the repository contains an app/application.conf folder" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("conf/application.conf","A_r","HMRC")(ec)).thenReturn(Future.successful(true))
      when(githubClient.repoContainsContent("conf/application.conf","D_r","DDCN")(ec)).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", repoType = RepoType.Deployable))),
        TeamRepositories("D", List(Repository("D_r", "url_D"))))
    }

    "Set repoType Service if the repository contains a Procfile" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("Procfile","A_r","HMRC")(ec)).thenReturn(Future.successful(true))
      when(githubClient.repoContainsContent("Procfile","B_r","HMRC")(ec)).thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", repoType = RepoType.Deployable))),
        TeamRepositories("D", List(Repository("D_r", "url_D", repoType = RepoType.Other))))
    }


    "Set type Service if the repository contains a deploy.properties" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))

      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("Procfile"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("deploy.properties"),same("D_r"),same("DDCN"))(same(ec))).thenReturn(Future.successful(true))

      dataSource.getTeamRepoMapping.futureValue should contain(TeamRepositories("D", List(Repository("D_r", "url_D", repoType = RepoType.Deployable))))
    }

    "Set type Library if not Service and has src/main/scala and has tags" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.getTags("HMRC", "A_r")(ec)).thenReturn(Future.successful(List.empty))
      when(githubClient.getTags("DDCN", "D_r")(ec)).thenReturn(Future.successful(List("D_r_tag")))

      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("Procfile"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("deploy.properties"),same("D_r"),same("DDCN"))(same(ec))).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("src/main/scala"),same("D_r"),same("DDCN"))(same(ec))).thenReturn(Future.successful(true))

      val repositories: Seq[TeamRepositories] = dataSource.getTeamRepoMapping.futureValue

      repositories should contain(TeamRepositories("D", List(Repository("D_r", "url_D", repoType = RepoType.Library))))

      repositories should contain(TeamRepositories("A", List(Repository("A_r", "url_A", repoType = RepoType.Other))))
    }


    "Set type Library if not Service and has src/main/java and has tags" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.getTags("HMRC", "A_r")(ec)).thenReturn(Future.successful(List.empty))
      when(githubClient.getTags("DDCN", "D_r")(ec)).thenReturn(Future.successful(List("D_r_tag")))

      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("Procfile"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("deploy.properties"),same("D_r"),same("DDCN"))(same(ec))).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("src/main/java"),same("D_r"),same("DDCN"))(same(ec))).thenReturn(Future.successful(true))

      val repositories: Seq[TeamRepositories] = dataSource.getTeamRepoMapping.futureValue

      repositories should contain(TeamRepositories("D", List(Repository("D_r", "url_D", repoType = RepoType.Library))))

      repositories should contain(TeamRepositories("A", List(Repository("A_r", "url_A", repoType = RepoType.Other))))
    }


    "Set type Other if not Service and Library" in {

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1),githubclient.GhOrganisation("DDCN",2))))
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(githubclient.GhTeam("D", 4))))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))
      when(githubClient.getReposForTeam(4)(ec)).thenReturn(Future.successful(List(githubclient.GhRepository("D_r", 4, "url_D"))))
      when(githubClient.getTags("HMRC", "A_r")(ec)).thenReturn(Future.successful(List.empty))
      when(githubClient.getTags("DDCN", "D_r")(ec)).thenReturn(Future.successful(List.empty))

      when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("Procfile"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent(same("deploy.properties"),same("D_r"),same("DDCN"))(same(ec))).thenReturn(Future.successful(false))

      val repositories: Seq[TeamRepositories] = dataSource.getTeamRepoMapping.futureValue

      repositories should contain(TeamRepositories("D", List(Repository("D_r", "url_D", repoType = RepoType.Other))))

      repositories should contain(TeamRepositories("A", List(Repository("A_r", "url_A", repoType = RepoType.Other))))
    }



    "Retry up to 5 times in the event of a failed api call" in {

      when(githubClient.getOrganisations(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(List(githubclient.GhOrganisation("HMRC",1))))

      when(githubClient.getTeamsForOrganisation("HMRC")(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(List(githubclient.GhTeam("A", 1))))

      when(githubClient.getReposForTeam(1)(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(List(githubclient.GhRepository("A_r", 1, "url_A"))))

      when(githubClient.repoContainsContent("app","A_r","HMRC")(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("Procfile","A_r","HMRC")(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(false))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A"))))
    }


  }

  private def createDataSource(githubClient: GithubApiClient): GithubV3RepositoryDataSource with GithubConfigProvider {def githubConfig: GithubConfig} = {
    new GithubV3RepositoryDataSource(githubClient, isInternal = false) with GithubConfigProvider {
      override def githubConfig: GithubConfig = new GithubConfig {
        override def hiddenRepositories: List[String] = testHiddenRepositories

        override def hiddenTeams: List[String] = testHiddenTeams
      }
    }
  }
}
