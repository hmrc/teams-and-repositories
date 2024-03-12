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

import cats.data.EitherT
import com.mongodb.{MongoWriteException, ServerAddress, WriteError}
import org.bson.BsonDocument
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues, Status => _}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{ACCEPTED, OK}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsJson
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsJson}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import scala.concurrent.Future


class WebhookControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with OptionValues with BeforeAndAfterEach {

  val mockPersistingService  : PersistingService       = mock[PersistingService]
  val mockRepositoriesService: RepositoriesPersistence = mock[RepositoriesPersistence]

  implicit override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .overrides(
        bind[PersistingService].toInstance(mockPersistingService),
        bind[RepositoriesPersistence].toInstance(mockRepositoriesService),
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPersistingService)
    reset(mockRepositoriesService)
  }

  private lazy val whroute  = routes.WebhookController.processGithubWebhook().url

  "WebhookController" should {

    "return 202 given 'push' webhook with a branch ref of 'main'" in {

      when(mockPersistingService.updateRepository(any())(any()))
        .thenReturn(EitherT[Future, String, Unit](Future.successful(Right(()))))

      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "repository": {
              |   "name": "foo"
              | },
              | "ref": "main"
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe ACCEPTED

      verify(mockPersistingService).updateRepository(any())(any())
    }

    "return 200 given 'push' webhook" in {
      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "repository": {
              |   "name": "foo"
              | },
              | "ref": "ab123"
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe OK

      verifyZeroInteractions(mockPersistingService)
    }

    "return 202 given 'team' webhook with action 'added_to_repository'" in {

      when(mockPersistingService.updateRepository(any())(any()))
        .thenReturn(EitherT[Future, String, Unit](Future.successful(Right(()))))

      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "action": "added_to_repository",
              | "team": {
              |   "name": "foo"
              | },
              | "repository": {
              |   "name": "bar"
              | }
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe ACCEPTED

      verify(mockPersistingService).updateRepository(any())(any())
    }

    "return 202 given 'team' webhook with action 'removed_from_repository'" in {

      when(mockPersistingService.updateRepository(any())(any()))
        .thenReturn(EitherT[Future, String, Unit](Future.successful(Right(()))))

      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "action": "removed_from_repository",
              | "team": {
              |   "name": "foo"
              | },
              | "repository": {
              |   "name": "bar"
              | }
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe ACCEPTED

      verify(mockPersistingService).updateRepository(any())(any())
    }

    "return 200 given 'team' webhook" in {

      when(mockPersistingService.updateRepository(any())(any()))
        .thenReturn(EitherT[Future, String, Unit](Future.successful(Right(()))))

      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "action": "baz",
              | "team": {
              |   "name": "foo"
              | },
              | "repository": {
              |   "name": "bar"
              | }
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe OK

      verifyZeroInteractions(mockPersistingService)
    }

    "return 202 given 'repository' webhook with 'archived' action" in {

      when(mockPersistingService.archiveRepository(any()))
        .thenReturn(Future.successful(()))

      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "action": "archived",
              | "repository": {
              |   "name": "bar"
              | }
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe ACCEPTED

      verify(mockPersistingService).archiveRepository(any())
    }

    "return 202 given 'repository' webhook with 'deleted' action and existing repo is found" in {
      when(mockPersistingService.deleteRepository(eqTo("foo"))(any()))
        .thenReturn(Future.successful(()))

      val request: FakeRequest[AnyContentAsJson] = FakeRequest(POST, whroute)
        .withJsonBody(
          Json.parse(
            """
              |{
              | "action": "deleted",
              | "repository": {
              |   "name": "foo"
              | }
              |}
              |""".stripMargin
          )
        )

      val result = route(app, request).value

      status(result) mustBe ACCEPTED

      verify(mockPersistingService).deleteRepository(any())(any())
    }
  }

}
