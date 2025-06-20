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
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.teamsandrepositories.connector.UserManagementConnector
import uk.gov.hmrc.teamsandrepositories.model.{GitRepository, Organisation, OpenPullRequest, RepoType, TeamSummary, User}
import uk.gov.hmrc.teamsandrepositories.persistence.{OpenPullRequestPersistence, RepositoriesPersistence, TeamSummaryPersistence}

import java.time.Instant
import scala.concurrent.Future

class OpenPullRequestsControllerSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with BeforeAndAfterEach:

  val mockOpenPullRequestPersistence: OpenPullRequestPersistence = mock[OpenPullRequestPersistence]
  val mockTeamSummaryPersistence    : TeamSummaryPersistence     = mock[TeamSummaryPersistence]
  val mockUserManagementConnector   : UserManagementConnector    = mock[UserManagementConnector]
  val mockRepositoriesPersistence   : RepositoriesPersistence    = mock[RepositoriesPersistence]

  implicit override lazy val app: Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[UserManagementConnector].toInstance(mockUserManagementConnector),
        bind[OpenPullRequestPersistence].toInstance(mockOpenPullRequestPersistence),
        bind[TeamSummaryPersistence].toInstance(mockTeamSummaryPersistence),
        bind[RepositoriesPersistence].toInstance(mockRepositoriesPersistence)
      )
      .build()

  override def beforeEach(): Unit =
    super.beforeEach()
    reset(mockOpenPullRequestPersistence)

  private def getOpenPrsRoute(repoName: Option[String], digitalServiceName: Option[String], raisedByMembersOfTeam: Option[String]) =
    routes.OpenPullRequestsController.getOpenPrs(repoName, digitalServiceName, raisedByMembersOfTeam).url

  private lazy val now = Instant.now()

  "OpenPullRequestsController" should:
    "get all open pull requests for repos owned by a team" in:
      when(mockTeamSummaryPersistence.findTeamSummaries(eqTo(Some("team-a"))))
        .thenReturn(
          Future.successful(Seq(
            TeamSummary("team-a", Some(now), Seq("example-repo1", "example-repo2"), now)
        )))

      when(mockOpenPullRequestPersistence.findOpenPullRequests(repos = eqTo(Some(Seq("example-repo1", "example-repo2"))), authors = any))
        .thenReturn(Future.successful(Seq(
          OpenPullRequest("example-repo1", "pr title 1", "https://github.com/example-repo1/pull/1", "author1", now),
          OpenPullRequest("example-repo1", "pr title 2", "https://github.com/example-repo1/pull/2", "author2", now),
          OpenPullRequest("example-repo2", "pr title 3", "https://github.com/example-repo2/pull/1", "author3", now),
          OpenPullRequest("example-repo2", "pr title 4", "https://github.com/example-repo2/pull/2", "author4", now)
        )))

      val result = route(app, FakeRequest(GET, getOpenPrsRoute(repoName = Some("team-a"), digitalServiceName = None, raisedByMembersOfTeam = None))).value

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.parse(s"""
        [
          {"repoName":"example-repo1","title":"pr title 1","url":"https://github.com/example-repo1/pull/1","author":"author1","createdAt":"$now"},
          {"repoName":"example-repo1","title":"pr title 2","url":"https://github.com/example-repo1/pull/2","author":"author2","createdAt":"$now"},
          {"repoName":"example-repo2","title":"pr title 3","url":"https://github.com/example-repo2/pull/1","author":"author3","createdAt":"$now"},
          {"repoName":"example-repo2","title":"pr title 4","url":"https://github.com/example-repo2/pull/2","author":"author4","createdAt":"$now"}
        ]
      """)

      verify(mockOpenPullRequestPersistence).findOpenPullRequests(repos = eqTo(Some(Seq("example-repo1", "example-repo2"))), authors = any)

    "get all open pull requests for repos owned by a digital service" in :
      when(mockRepositoriesPersistence.find(any, any, any, any, eqTo(Some("a digital service")), any, any, any, any))
        .thenReturn(
          Future.successful(Seq(
            GitRepository(
              name               = "example-repo1",
              organisation       = Some(Organisation.Mdtp),
              description        = "Some Description",
              url                = "https://github.com/org/example-repo1",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              isArchived         = false,
              defaultBranch      = "main",
              digitalServiceName = Some("a digital service"),
              lastUpdated        = now
            ),
            GitRepository(
              name               = "example-repo2",
              organisation       = Some(Organisation.Mdtp),
              description        = "Some Description",
              url                = "https://github.com/org/example-repo2",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              isArchived         = false,
              defaultBranch      = "main",
              digitalServiceName = Some("a digital service"),
              lastUpdated        = now
            )
          )))

      when(mockOpenPullRequestPersistence.findOpenPullRequests(repos = eqTo(Some(Seq("example-repo1", "example-repo2"))), authors = any))
        .thenReturn(Future.successful(Seq(
          OpenPullRequest("example-repo1", "pr title 1", "https://github.com/example-repo1/pull/1", "author1", now),
          OpenPullRequest("example-repo1", "pr title 2", "https://github.com/example-repo1/pull/2", "author2", now),
          OpenPullRequest("example-repo2", "pr title 3", "https://github.com/example-repo2/pull/1", "author3", now),
          OpenPullRequest("example-repo2", "pr title 4", "https://github.com/example-repo2/pull/2", "author4", now)
        )))

      val result = route(app, FakeRequest(GET, getOpenPrsRoute(repoName = None, digitalServiceName = Some("a digital service"), raisedByMembersOfTeam = None))).value

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
        [
          {"repoName":"example-repo1","title":"pr title 1","url":"https://github.com/example-repo1/pull/1","author":"author1","createdAt":"$now"},
          {"repoName":"example-repo1","title":"pr title 2","url":"https://github.com/example-repo1/pull/2","author":"author2","createdAt":"$now"},
          {"repoName":"example-repo2","title":"pr title 3","url":"https://github.com/example-repo2/pull/1","author":"author3","createdAt":"$now"},
          {"repoName":"example-repo2","title":"pr title 4","url":"https://github.com/example-repo2/pull/2","author":"author4","createdAt":"$now"}
        ]
      """)

      verify(mockOpenPullRequestPersistence).findOpenPullRequests(repos = eqTo(Some(Seq("example-repo1", "example-repo2"))), any)

    "get all open pull requests raised by members of a team" in :
      when(mockUserManagementConnector.getUsersForTeam(eqTo("team-a")))
        .thenReturn(
          Future.successful(Seq(
            User(Some("author1")),
            User(Some("author2"))
          )))

      when(mockOpenPullRequestPersistence.findOpenPullRequests(repos = any, authors = eqTo(Some(Seq("author1", "author2")))))
        .thenReturn(Future.successful(Seq(
          OpenPullRequest("example-repo1", "pr title 1", "https://github.com/example-repo1/pull/1", "author1", now),
          OpenPullRequest("example-repo2", "pr title 2", "https://github.com/example-repo2/pull/2", "author2", now),
          OpenPullRequest("example-repo3", "pr title 3", "https://github.com/example-repo3/pull/1", "author1", now),
          OpenPullRequest("example-repo4", "pr title 4", "https://github.com/example-repo4/pull/2", "author2", now)
        )))

      val result = route(app, FakeRequest(GET, getOpenPrsRoute(repoName = None, digitalServiceName = None, raisedByMembersOfTeam = Some("team-a")))).value

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
        [
          {"repoName":"example-repo1","title":"pr title 1","url":"https://github.com/example-repo1/pull/1","author":"author1","createdAt":"$now"},
          {"repoName":"example-repo2","title":"pr title 2","url":"https://github.com/example-repo2/pull/2","author":"author2","createdAt":"$now"},
          {"repoName":"example-repo3","title":"pr title 3","url":"https://github.com/example-repo3/pull/1","author":"author1","createdAt":"$now"},
          {"repoName":"example-repo4","title":"pr title 4","url":"https://github.com/example-repo4/pull/2","author":"author2","createdAt":"$now"}
        ]
      """)

      verify(mockOpenPullRequestPersistence).findOpenPullRequests(repos = any, authors = eqTo(Some(Seq("author1", "author2"))))
