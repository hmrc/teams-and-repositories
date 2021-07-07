/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.controller

import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.DataReloadScheduler
import uk.gov.hmrc.teamsandrepositories.persistence.TeamsAndReposPersister

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AdminControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with Results
     with OptionValues
     with GuiceOneServerPerSuite
     with Eventually {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "github.open.api.host" -> "http://bla.bla",
          "github.open.api.user" -> "",
          "github.open.api.key"  -> "",
          "metrics.jvm"          -> false
        )
      )
      .build

  "resetLastActiveDate" should {
    "return OK if the persister return Some" in new Setup {
      val repoName = "repo-name"

      when(mockTeamsAndReposPersister.resetLastActiveDate(repoName))
        .thenReturn(Future.successful(Some(1L)))

      val result = controller.resetLastActiveDate(repoName)(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("message" -> s"'$repoName' last active date reset for 1 team(s)")
    }

    "return NOT_FOUND if the persister return None" in new Setup {
      val repoName = "repo-name"

      when(mockTeamsAndReposPersister.resetLastActiveDate(repoName))
        .thenReturn(Future.successful(None))

      val result = controller.resetLastActiveDate(repoName)(FakeRequest())
      status(result) mustBe NOT_FOUND
    }
  }

  private trait Setup {
    val mockDataReloadScheduler    = mock[DataReloadScheduler]
    val mockTeamsAndReposPersister = mock[TeamsAndReposPersister]

    val controller = new AdminController(
      mockDataReloadScheduler,
      mockTeamsAndReposPersister,
      stubControllerComponents()
    )
  }
}
