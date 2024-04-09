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
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.models.{DeletedGitRepository, RepoType, ServiceType}
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
  private def getRoute(name: Option[String] = None, repoType: Option[RepoType] = None, serviceType: Option[ServiceType] = None) =
    routes.DeletedRepositoriesController.getDeletedRepos(name = name, team = None, repoType = repoType, serviceType = serviceType).url

  private lazy val now = Instant.now()

  "DeletedRepositoriesController" should {

    "get all deleted repositories" in {
      when(mockDeletedRepositoriesPersistence.find(any(), any(), any(), any()))
        .thenReturn(Future.successful(Seq(
          DeletedGitRepository("Foo", now)
        , DeletedGitRepository("Bar", now)
        )))

      val result = route(app, FakeRequest(GET, getRoute())).value

      status(result)        mustBe OK
      contentAsJson(result) mustBe Json.parse(s"""
        [
          {"name":"Foo","deletedDate":"$now"}
        , {"name":"Bar","deletedDate":"$now"}
        ]
      """)

      verify(mockDeletedRepositoriesPersistence).find(eqTo(None), eqTo(None), eqTo(None), eqTo(None))
    }

    "get deleted repository by name" in {
      when(mockDeletedRepositoriesPersistence.find(any(), any(), any(), any()))
        .thenReturn(Future.successful(Seq(
          DeletedGitRepository("Foo", now)
        )))

      val result = route(app, FakeRequest(GET, getRoute(name = Some("Foo")))).value

      status(result)        mustBe OK
      contentAsJson(result) mustBe Json.parse(s"""
        [
          {"name":"Foo","deletedDate":"$now"}
        ]
      """)

      verify(mockDeletedRepositoriesPersistence).find(eqTo(Some("Foo")), eqTo(None), eqTo(None), eqTo(None))
    }

    "get all deleted services" in {
      when(mockDeletedRepositoriesPersistence.find(any(), any(), any(), any()))
        .thenReturn(Future.successful(Seq(
          DeletedGitRepository("Foo", now)
        )))

      val result = route(app, FakeRequest(GET, getRoute(repoType = Some(RepoType.Service)))).value

      status(result)        mustBe OK
      contentAsJson(result) mustBe Json.parse(s"""
        [
          {"name":"Foo","deletedDate":"$now"}
        ]
      """)

      verify(mockDeletedRepositoriesPersistence).find(eqTo(None), eqTo(None), eqTo(Some(RepoType.Service)), eqTo(None))
    }
  }
}
