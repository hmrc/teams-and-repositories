///*
// * Copyright 2016 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.catalogue.github
//
//import org.mockito.Mockito._
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.mock.MockitoSugar
//import org.scalatest.{Matchers, WordSpec}
//import uk.gov.hmrc.catalogue.DefaultPatienceConfig
//import uk.gov.hmrc.catalogue.teams.GithubEnterpriseDataSource
//import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, Team}
//
//import scala.concurrent.Future
//
//class GithubEnterpriseSpec extends WordSpec with MockitoSugar with Matchers with ScalaFutures with DefaultPatienceConfig {
//
//  val githubHttp: GithubHttp = mock[GithubHttp]
//  val gitHub = new GithubEnterpriseDataSource {
//    val gh = githubHttp
//  }
//
//  "getTeamRepoMapping" should {
//    "return mapping of team to repositories they own" in {
//
//      val organizations: List[GhOrganization] = List(
//        GhOrganization("HMRC"),
//        GhOrganization("DDCN")
//      )
//
//      val teamsForHmrc: List[GhTeam] = List(GhTeam("A", 1), GhTeam("B", 2))
//      val teamsForDDCN: List[GhTeam] = List(GhTeam("C", 3))
//
//      val result: Future[List[Team]] = gitHub.getTeamRepoMapping
//      result.futureValue shouldBe List(
//        Team("A", List(Repository("A_r", "url_A"))),
//        Team("B", List(Repository("B_r", "url_B"))),
//        Team("C", List(Repository("C_r", "url_C")))
//      )
//
//      when(githubHttp.getOrganisations).thenReturn(Future.successful(organizations))
//
//      when(githubHttp.getTeamsForOrganisation(GhOrganization("HMRC"))).thenReturn(Future.successful(teamsForHmrc))
//      when(githubHttp.getTeamsForOrganisation(GhOrganization("DDCN"))).thenReturn(Future.successful(teamsForDDCN))
//
//      when(githubHttp.getReposForTeam(GhTeam(name = "A", id = 1))).thenReturn(Future.successful(List(GhRepository("A_r", 4, "url_A"))))
//      when(githubHttp.getReposForTeam(GhTeam(name = "B", id = 2))).thenReturn(Future.successful(List(GhRepository("B_r", 5, "url_B"))))
//      when(githubHttp.getReposForTeam(GhTeam(name = "C", id = 3))).thenReturn(Future.successful(List(GhRepository("C_r", 6, "url_C"))))
//
//
//
//    }
//  }
//}
