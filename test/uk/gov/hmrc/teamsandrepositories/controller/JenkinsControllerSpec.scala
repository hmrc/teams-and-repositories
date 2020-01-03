/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import scala.concurrent.Future

class JenkinsControllerSpec extends PlaySpec with Results with MockitoSugar {

  val mockJenkinsService = mock[JenkinsService]

  "JenkinsController" should {
    "return a single match as Json" in {
      when(mockJenkinsService.findByService("foo")) thenReturn Future.successful(Some(BuildJob("foo", "http://bar/job/api/")))

      val controller = new JenkinsController(mockJenkinsService, stubControllerComponents())
      val result = controller.lookup("foo").apply(FakeRequest())
      val bodyText = contentAsString(result)
      bodyText mustBe """{"service":"foo","jenkinsURL":"http://bar/job/api/"}"""
    }

    "return a no content when no matches found" in {
      when(mockJenkinsService.findByService("bar")) thenReturn Future.successful(None)

      val controller = new JenkinsController(mockJenkinsService, stubControllerComponents())
      val result = controller.lookup("bar").apply(FakeRequest())
      status(result) mustBe 204
    }
  }
}
