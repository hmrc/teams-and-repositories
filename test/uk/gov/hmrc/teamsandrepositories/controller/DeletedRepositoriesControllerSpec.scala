/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.Results.NoContent
import play.api.mvc.{AnyContent, AnyContentAsEmpty, AnyContentAsJson}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.models.DeletedGitRepository
import uk.gov.hmrc.teamsandrepositories.persistence.DeletedRepositoriesPersistence

import java.time.Instant
import scala.concurrent.Future

class DeletedRepositoriesControllerSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with BeforeAndAfterEach {

  val mockDeletedRepositoriesPersistence: DeletedRepositoriesPersistence = mock[DeletedRepositoriesPersistence]

  implicit override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .overrides(
        bind[DeletedRepositoriesPersistence].toInstance(mockDeletedRepositoriesPersistence)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDeletedRepositoriesPersistence)
  }
  private def getRoute(name: Option[String])  = routes.DeletedRepositoriesController.getDeletedRepos(name, None).url

  private lazy val now = Instant.now()

  "DeletedRepositoriesController" should {

    "get all deleted repositories" in {

      implicit val reads: OFormat[DeletedGitRepository] = DeletedGitRepository.apiFormat

      val expectedModel = Seq(
        DeletedGitRepository("Foo", now),
        DeletedGitRepository("Bar", now),
      )

      when(mockDeletedRepositoriesPersistence.find(any(), any()))
        .thenReturn(Future.successful(expectedModel))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, getRoute(None))

      val result = route(app, request).value

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(expectedModel)

      verify(mockDeletedRepositoriesPersistence).find(eqTo(None), eqTo(None))
    }

    "get deleted repository by name" in {

      implicit val reads: OFormat[DeletedGitRepository] = DeletedGitRepository.apiFormat

      val expectedModel = Seq(
        DeletedGitRepository("Foo", now)
      )

      when(mockDeletedRepositoriesPersistence.find(any(), any()))
        .thenReturn(Future.successful(expectedModel))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, getRoute(Some("Foo")))

      val result = route(app, request).value

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(expectedModel)

      verify(mockDeletedRepositoriesPersistence).find(eqTo(Some("Foo")), eqTo(None))
    }
  }
}
