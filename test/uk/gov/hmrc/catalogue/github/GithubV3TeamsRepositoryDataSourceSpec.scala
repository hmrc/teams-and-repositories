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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Matchers
import uk.gov.hmrc.catalogue.DefaultPatienceConfig
import uk.gov.hmrc.catalogue.teams.{GithubV3TeamsRepositoryDataSource, ViewModels}
import ViewModels.{Repository, Team}

class GithubV3TeamsRepositoryDataSourceSpec extends GithubWireMockSpec with ScalaFutures with Matchers with DefaultPatienceConfig  {

  val githubApiClient = new GithubV3ApiClient with TestEndpoints with TestCredentials
  val dataSource = new GithubV3TeamsRepositoryDataSource(githubApiClient)

  "Github v3 Data Source" should {

    "Return a list of teams and repositories, filtering out forks" in {

      githubReturns(Map[GhOrganization, Map[GhTeam, List[GhRepository]]] (
        GhOrganization("HMRC") -> Map(
          GhTeam("A", 1) -> List(GhRepository("A_r", 1, "url_A"), GhRepository("A_r2", 5, "url_A2", fork = true)),
          GhTeam("B", 2) -> List(GhRepository("B_r", 2, "url_B"))),
        GhOrganization("DDCN") -> Map(
          GhTeam("C", 3) -> List(GhRepository("C_r", 3, "url_C")),
          GhTeam("D", 4) -> List(GhRepository("D_r", 4, "url_D", fork = true)))))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        Team("A", List(Repository("A_r", "url_A"))),
        Team("B", List(Repository("B_r", "url_B"))),
        Team("C", List(Repository("C_r", "url_C"))),
        Team("D", List()))
    }
  }
}
