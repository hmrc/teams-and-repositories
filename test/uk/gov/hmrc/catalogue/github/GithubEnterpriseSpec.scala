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

import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Seconds, Millis, Span}
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.catalogue.github.Model.{Repository, Team}

import scala.concurrent.Future

class GithubEnterpriseSpec extends WordSpec with MockitoSugar with Matchers with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  val githubHttp: GithubHttp = mock[GithubHttp]
  val gitHub = new GithubEnterprise {
    val gh = githubHttp
  }

  "getTeamRepoMapping" should {
    "return mapping of team to repositories they own" in {

      val organizations: List[GhOrganization] = List(
        GhOrganization("HMRC"),
        GhOrganization("DDCN")
      )

      val teamsForHmrc: List[GhTeam] = List(GhTeam("A", 1), GhTeam("B", 2))
      val teamsForDDCN: List[GhTeam] = List(GhTeam("C", 3))



      when(githubHttp.host).thenReturn("myHost")

      when(githubHttp.get[List[GhOrganization]](eqTo("https://myHost/api/v3/user/orgs"))(any())).thenReturn(Future.successful(organizations))

      when(githubHttp.get[List[GhTeam]](eqTo(s"https://myHost/api/v3/orgs/HMRC/teams?per_page=100"))(any())).thenReturn(Future.successful(teamsForHmrc))
      when(githubHttp.get[List[GhTeam]](eqTo(s"https://myHost/api/v3/orgs/DDCN/teams?per_page=100"))(any())).thenReturn(Future.successful(teamsForDDCN))

      when(githubHttp.get[List[GhRepository]](eqTo(s"https://myHost/api/v3/teams/1/repos?per_page=100"))(any())).thenReturn(Future.successful(List(GhRepository("A_r", 4, "url_A"))))
      when(githubHttp.get[List[GhRepository]](eqTo(s"https://myHost/api/v3/teams/2/repos?per_page=100"))(any())).thenReturn(Future.successful(List(GhRepository("B_r", 5, "url_B"))))
      when(githubHttp.get[List[GhRepository]](eqTo("https://myHost/api/v3/teams/3/repos?per_page=100"))(any())).thenReturn(Future.successful(List(GhRepository("C_r", 6, "url_C"))))

      val result: Future[List[Team]] = gitHub.getTeamRepoMapping
      result.futureValue shouldBe List(
        Team("A", List(Repository("A_r", "url_A"))),
        Team("B", List(Repository("B_r", "url_B"))),
        Team("C", List(Repository("C_r", "url_C")))
      )

    }
  }
}
