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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, Writes}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.teamsandrepositories.model.OpenPullRequest
import uk.gov.hmrc.teamsandrepositories.persistence.OpenPullRequestPersistence

import java.time.Instant
import scala.concurrent.Future

class OpenPullRequestControllerSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with BeforeAndAfterEach:

  val mockOpenPullRequestPersistence: OpenPullRequestPersistence = mock[OpenPullRequestPersistence]

  implicit override lazy val app: Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[OpenPullRequestPersistence].toInstance(mockOpenPullRequestPersistence)
      )
      .build()

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(mockOpenPullRequestPersistence)

  private def getByRepoRoute(repoName: String) =
    routes.OpenPullRequestsController.getOpenPrsByRepo(repoName = repoName).url

  private def getByAuthorRoute(author: String) =
    routes.OpenPullRequestsController.getOpenPrsByAuthor(author = author).url

  private lazy val now = Instant.now()

  private given Writes[Instant] = MongoJavatimeFormats.instantFormat

  "OpenPullRequestsController" should:

    "get all open pull requests by repository" in:
      when(mockOpenPullRequestPersistence.findOpenPullRequestsByRepo(any()))
        .thenReturn(Future.successful(Seq(
          OpenPullRequest("example-repo", "pr title 1", "https://github.com/example-repo/pull/1", "author1", now),
          OpenPullRequest("example-repo", "pr title 2", "https://github.com/example-repo/pull/2", "author2", now)
        )))

      val result = route(app, FakeRequest(GET, getByRepoRoute("example-repo"))).value

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.parse(s"""
        [
          {"repoName":"example-repo","title":"pr title 1","url":"https://github.com/example-repo/pull/1","author":"author1","createdAt":${Json.toJson(now)}},
          {"repoName":"example-repo","title":"pr title 2","url":"https://github.com/example-repo/pull/2","author":"author2","createdAt":${Json.toJson(now)}}
        ]
      """)

      verify(mockOpenPullRequestPersistence).findOpenPullRequestsByRepo(eqTo("example-repo"))

    "get all open pull requests by author" in :
      when(mockOpenPullRequestPersistence.findOpenPullRequestsByAuthor(any()))
        .thenReturn(Future.successful(Seq(
          OpenPullRequest("example-repo1", "pr title 1", "https://github.com/example-repo1/pull/1", "author1", now),
          OpenPullRequest("example-repo2", "pr title 2", "https://github.com/example-repo2/pull/1", "author1", now)
        )))

      val result = route(app, FakeRequest(GET, getByAuthorRoute("author1"))).value

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
        [
          {"repoName":"example-repo1","title":"pr title 1","url":"https://github.com/example-repo1/pull/1","author":"author1","createdAt":${Json.toJson(now)}},
          {"repoName":"example-repo2","title":"pr title 2","url":"https://github.com/example-repo2/pull/1","author":"author1","createdAt":${Json.toJson(now)}}
        ]
      """)

      verify(mockOpenPullRequestPersistence).findOpenPullRequestsByAuthor(eqTo("author1"))
