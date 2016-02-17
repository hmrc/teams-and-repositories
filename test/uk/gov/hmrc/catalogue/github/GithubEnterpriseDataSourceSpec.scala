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
import uk.gov.hmrc.catalogue.github.Model.{Repository, Team}

class GithubEnterpriseDataSourceSpec extends GithubWireMockSpec with ScalaFutures with Matchers with DefaultPatienceConfig  {

  val githubHttp = new GithubHttp with GithubEnterpriseApiEndpoints {
    override def rootUrl: String = s"http://$testHost:$port"
    override val cred : ServiceCredentials = ServiceCredentials(rootUrl, "", "")
  }

  val dataSource = new GithubEnterpriseDataSource {
    def gh = githubHttp
  }

  "Github Enterprise Data Source" should {

    "Return a list of teams and repositories" in {

      githubReturns(Map[GhOrganization, Map[GhTeam, List[GhRepository]]] (
        GhOrganization("HMRC") -> Map(
          GhTeam("A", 1) -> List(GhRepository("A_r", 1, "url_A")), 
          GhTeam("B", 2) -> List(GhRepository("B_r", 2, "url_B"))),
        GhOrganization("DDCN") -> Map(
          GhTeam("C", 3) -> List(GhRepository("C_r", 3, "url_C")))))

      dataSource.getTeamRepoMapping.futureValue shouldBe List(
        Team("A", List(Repository("A_r", "url_A"))),
        Team("B", List(Repository("B_r", "url_B"))),
        Team("C", List(Repository("C_r", "url_C")))
      )

    }
  }
}
