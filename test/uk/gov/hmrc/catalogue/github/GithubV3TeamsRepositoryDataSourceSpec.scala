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

package uk.gov.hmrc.catalogue.github

import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import play.api.test.{FakeApplication, WithApplication}
import uk.gov.hmrc.catalogue.DefaultPatienceConfig
import uk.gov.hmrc.catalogue.teams.GithubV3TeamsRepositoryDataSource
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, TeamRepositories}

class GithubV3TeamsRepositoryDataSourceSpec extends GithubWireMockSpec with ScalaFutures with Matchers with DefaultPatienceConfig  {

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams = List("hidden_team1", "hidden_team2")
  val githubApiClient = new GithubV3ApiClient with TestEndpoints with TestCredentials {
    override def isInternal: Boolean = true
  }
  val dataSource = new GithubV3TeamsRepositoryDataSource(githubApiClient) with GithubConfigProvider {
    override def githubConfig: GithubConfig = new GithubConfig {
      override def hiddenRepositories: List[String] = testHiddenRepositories

      override def hiddenTeams: List[String] = testHiddenTeams
    }
  }

  "Github v3 Data Source" should {

    "Return a list of teams and repositories, filtering out forks" in {

      githubReturns(Map[GhOrganisation, Map[GhTeam, List[GhRepository]]] (
        GhOrganisation("HMRC") -> Map(
          GhTeam("A", 1) -> List(GhRepository("A_r", 1, "url_A"), GhRepository("A_r2", 5, "url_A2", fork = true)),
          GhTeam("B", 2) -> List(GhRepository("B_r", 2, "url_B"))),
        GhOrganisation("DDCN") -> Map(
          GhTeam("C", 3) -> List(GhRepository("C_r", 3, "url_C")),
          GhTeam("D", 4) -> List(GhRepository("D_r", 4, "url_D", fork = true)))))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", isInternal = true))),
        TeamRepositories("B", List(Repository("B_r", "url_B", isInternal = true))),
        TeamRepositories("C", List(Repository("C_r", "url_C", isInternal = true))),
        TeamRepositories("D", List()))
    }

    "Filter out repositories according to the hidden config" in new WithApplication(
      FakeApplication(additionalConfiguration = Map("github.hidden.repositories" -> testHiddenRepositories.mkString(",")))) {

      githubReturns(Map[GhOrganisation, Map[GhTeam, List[GhRepository]]] (
        GhOrganisation("HMRC") -> Map(
          GhTeam("A", 1) -> List(GhRepository("hidden_repo1", 1, "url_A"), GhRepository("A_r2", 5, "url_A2"))),
        GhOrganisation("DDCN") -> Map(
          GhTeam("C", 3) -> List(GhRepository("hidden_repo2", 3, "url_C")),
          GhTeam("D", 4) -> List(GhRepository("D_r", 4, "url_D")))))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r2", "url_A2", isInternal = true))),
        TeamRepositories("C", List()),
        TeamRepositories("D", List(Repository("D_r", "url_D", isInternal = true))))

    }

    "Filter out teams according to the hidden config" in {

      githubReturns(Map[GhOrganisation, Map[GhTeam, List[GhRepository]]] (
        GhOrganisation("HMRC") -> Map(
          GhTeam("hidden_team1", 1) -> List(GhRepository("A_r", 1, "url_A"))),
        GhOrganisation("DDCN") -> Map(
          GhTeam("hidden_team2", 3) -> List(GhRepository("C_r", 3, "url_C")),
          GhTeam("D", 4) -> List(GhRepository("D_r", 4, "url_D")))))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("D", List(Repository("D_r", "url_D", isInternal = true))))

    }

    "Set microservice=true if the repository contains an app folder" in {

      githubReturns(Map[GhOrganisation, Map[GhTeam, List[GhRepository]]] (
        GhOrganisation("HMRC") -> Map(
          GhTeam("A", 1) -> List(GhRepository("A_r", 1, "url_A")),
          GhTeam("B", 2) -> List(GhRepository("B_r", 2, "url_B"))),
        GhOrganisation("DDCN") -> Map(
          GhTeam("C", 3) -> List(GhRepository("C_r", 3, "url_C")),
          GhTeam("D", 4) -> List(GhRepository("D_r", 4, "url_D")))))

      repositoryContainsAppFolder("HMRC", "A_r")
      repositoryContainsAppFolder("DDCN", "C_r")

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        TeamRepositories("A", List(Repository("A_r", "url_A", isInternal = true , isMicroservice = true))),
        TeamRepositories("B", List(Repository("B_r", "url_B", isInternal = true))),
        TeamRepositories("C", List(Repository("C_r", "url_C", isInternal = true , isMicroservice = true))),
        TeamRepositories("D", List(Repository("D_r", "url_D", isInternal = true))))

    }
  }
}
