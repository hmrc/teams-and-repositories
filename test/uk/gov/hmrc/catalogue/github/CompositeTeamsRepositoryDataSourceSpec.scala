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

import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpec, Matchers}
import uk.gov.hmrc.catalogue.DefaultPatienceConfig
import uk.gov.hmrc.catalogue.teams.{CompositeTeamsRepositoryDataSource, TeamsRepositoryDataSource}
import uk.gov.hmrc.catalogue.teams.ViewModels.{Team, Repository}

import scala.concurrent.Future

class CompositeTeamsRepositoryDataSourceSpec extends WordSpec with MockitoSugar with ScalaFutures with Matchers with DefaultPatienceConfig {

  "Retrieving team repo mappings" should {

    "return the combination of all input sources"  in {

      val teamsList1 = List(
        Team("A", List(Repository("A_r", "url_A"))),
        Team("B", List(Repository("B_r", "url_B"))),
        Team("C", List(Repository("C_r", "url_C"))))

      val teamsList2 = List(
        Team("D", List(Repository("D_r", "url_D"))),
        Team("E", List(Repository("E_r", "url_E"))),
        Team("F", List(Repository("F_r", "url_F"))))

      val dataSource1 = mock[TeamsRepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(Future.successful(teamsList1))

      val dataSource2 = mock[TeamsRepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(Future.successful(teamsList2))

      val compositeDataSource = new CompositeTeamsRepositoryDataSource(List(dataSource1, dataSource2))
      val result = compositeDataSource.getTeamRepoMapping.futureValue

      result.length shouldBe 6
      result should contain (teamsList1.head)
      result should contain (teamsList1(1))
      result should contain (teamsList1(2))
      result should contain (teamsList2.head)
      result should contain (teamsList2(1))
      result should contain (teamsList2(2))

    }

    "combine teams that have the same names in both sources"  in {

      val teamsList1 = List(
        Team("A", List(Repository("A_r", "url_A"))),
        Team("B", List(Repository("B_r", "url_B"))),
        Team("C", List(Repository("C_r", "url_C"))))

      val teamsList2 = List(
        Team("A", List(Repository("A_r2", "url_A2"))),
        Team("D", List(Repository("D_r", "url_D"))))

      val dataSource1 = mock[TeamsRepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(Future.successful(teamsList1))

      val dataSource2 = mock[TeamsRepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(Future.successful(teamsList2))

      val compositeDataSource = new CompositeTeamsRepositoryDataSource(List(dataSource1, dataSource2))
      val result = compositeDataSource.getTeamRepoMapping.futureValue

      result.length shouldBe 4
      result should contain (Team(teamsList1.head.teamName, teamsList1.head.repositories ++ teamsList2.head.repositories))
      result should contain (teamsList1(1))
      result should contain (teamsList1(2))
      result should contain (teamsList2(1))

    }
  }
}
