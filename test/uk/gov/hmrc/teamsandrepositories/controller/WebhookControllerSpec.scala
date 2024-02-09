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
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence}
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import java.time.Instant
import scala.concurrent.Future


class WebhookControllerSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with OptionValues with BeforeAndAfterEach {

  val mockPersistingService: PersistingService                            = mock[PersistingService]
  val mockRepositoriesService: RepositoriesPersistence                    = mock[RepositoriesPersistence]
  val mockDeletedRepositoriesPersistence: DeletedRepositoriesPersistence  = mock[DeletedRepositoriesPersistence]

  implicit override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .overrides(
        bind[PersistingService].toInstance(mockPersistingService),
        bind[RepositoriesPersistence].toInstance(mockRepositoriesService),
        bind[DeletedRepositoriesPersistence].toInstance(mockDeletedRepositoriesPersistence),
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPersistingService)
    reset(mockRepositoriesService)
    reset(mockDeletedRepositoriesPersistence)
  }

  private val now = Instant.now()

  private val repo =
    GitRepository(
      name = "a-library",
      description = "Some Description",
      url = "https://github.com/org/a-library",
      createdDate = now,
      lastActiveDate = now,
      isPrivate = true,
      repoType = RepoType.Library,
      language = Some("Scala"),
      isArchived = false,
      defaultBranch = "main",
    )

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

      when(mockPersistingService.repositoryArchived(any()))
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

      verify(mockPersistingService).repositoryArchived(any())
    }

    "return 202 given 'repository' webhook with 'deleted' action and existing repo is found" in {

      when(mockRepositoriesService.findRepo(eqTo("foo")))
        .thenReturn(Future.successful(Some(repo)))

      when(mockDeletedRepositoriesPersistence.set(any()))
        .thenReturn(Future.unit)

      when(mockPersistingService.repositoryDeleted(eqTo("foo")))
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

      verify(mockRepositoriesService).findRepo(any())
      verify(mockDeletedRepositoriesPersistence).set(any())
      verify(mockPersistingService).repositoryDeleted(any())
    }

    "return 202 given 'repository' webhook with 'deleted' action and no existing repo is found" in {

      when(mockRepositoriesService.findRepo(eqTo("foo")))
        .thenReturn(Future.successful(None))

      when(mockDeletedRepositoriesPersistence.set(any()))
        .thenReturn(Future.unit)

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

      verify(mockRepositoriesService).findRepo(any())
      verify(mockDeletedRepositoriesPersistence).set(any())
      verifyZeroInteractions(mockPersistingService)
    }
  }

}
