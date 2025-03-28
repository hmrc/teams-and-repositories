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

import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.teamsandrepositories.controller.v2.TeamsAndRepositoriesController
import uk.gov.hmrc.teamsandrepositories.model.{DeletedGitRepository, Organisation, GitRepository, RepoType, ServiceType}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence, TeamSummaryPersistence}
import uk.gov.hmrc.teamsandrepositories.service.BranchProtectionService

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndRepositoriesControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with OptionValues:

  "TeamsAndRepositoriesController" should:
    "return all decommissioned repos" in new Setup:

      when(mockRepositoriesPersistence.find(isArchived = Some(true), repoType = None))
        .thenReturn(Future.successful(Seq(
          aRepo.copy(name = "repo-1", repoType=RepoType.Service),
          aRepo.copy(name = "repo-2", repoType=RepoType.Library)
        )))

      when(mockDeletedRepositoriesPersistence.find(repoType = None))
        .thenReturn(Future.successful(Seq(
          aDeletedRepo.copy(name = "repo-3", repoType = Some(RepoType.Other)),
          aDeletedRepo.copy(name = "repo-4", repoType = None)
        )))


      val result: Future[Result] =
        controller.decommissionedRepos().apply(FakeRequest())

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
           |[
           |  {
           |    "repoName": "repo-1",
           |    "repoType": "Service"
           |  },
           |  {
           |    "repoName": "repo-2",
           |    "repoType": "Library"
           |   },
           |  {
           |    "repoName": "repo-3",
           |    "repoType": "Other"
           |  },
           |  {
           |    "repoName": "repo-4"
           |  }
           |]
           |""".stripMargin)

    "return all decommissioned services" in new Setup:

      val service: RepoType = RepoType.Service

      when(mockRepositoriesPersistence.find(isArchived = Some(true), repoType = Some(service)))
        .thenReturn(Future.successful(Seq(
          aRepo.copy(name = "repo-1", repoType=service),
          aRepo.copy(name = "repo-2", repoType=service)
        )))

      when(mockDeletedRepositoriesPersistence.find(repoType = Some(service)))
        .thenReturn(Future.successful(Seq(
          aDeletedRepo.copy(name = "repo-3", repoType = Some(service)),
          aDeletedRepo.copy(name = "repo-4", repoType = Some(service))
        )))


      val result: Future[Result] =
        controller.decommissionedRepos(repoType = Some(service)).apply(FakeRequest())

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
           |[
           |  {
           |    "repoName": "repo-1",
           |    "repoType": "Service"
           |  },
           |  {
           |    "repoName": "repo-2",
           |    "repoType": "Service"
           |   },
           |  {
           |    "repoName": "repo-3",
           |    "repoType": "Service"
           |  },
           |  {
           |    "repoName": "repo-4",
           |    "repoType": "Service"
           |  }
           |]
           |""".stripMargin)

  trait Setup:
    val mockDeletedRepositoriesPersistence: DeletedRepositoriesPersistence = mock[DeletedRepositoriesPersistence]
    val mockRepositoriesPersistence       : RepositoriesPersistence        = mock[RepositoriesPersistence]
    val mockBranchProtectionService       : BranchProtectionService        = mock[BranchProtectionService]
    val mockTeamSummaryPersistence        : TeamSummaryPersistence         = mock[TeamSummaryPersistence]
    val mockAuthComponents                : BackendAuthComponents          = mock[BackendAuthComponents]

    val controller: TeamsAndRepositoriesController =
      TeamsAndRepositoriesController(
        mockRepositoriesPersistence,
        mockTeamSummaryPersistence,
        mockDeletedRepositoriesPersistence,
        mockBranchProtectionService,
        mockAuthComponents,
        stubControllerComponents()
      )

    private lazy val now = Instant.now()

    val aRepo: GitRepository =
      GitRepository(
        name                 = "",
        organisation         = Some(Organisation.Mdtp),
        description          = "a-repo",
        url                  = "repo-url",
        createdDate          = now,
        lastActiveDate       = now,
        repoType             = RepoType.Service,
        serviceType          = Some(ServiceType.Backend),
        tags                 = None,
        digitalServiceName   = None,
        owningTeams          = Seq.empty,
        language             = Some("scala"),
        isArchived           = true,
        defaultBranch        = "branch",
        branchProtection     = None,
        isDeprecated         = true,
        teamNames            = List.empty,
        prototypeName        = None,
        prototypeAutoPublish = None,
        repositoryYamlText   = None
    )

    val aDeletedRepo: DeletedGitRepository =
      DeletedGitRepository(
        name               = "",
        deletedDate        = now,
        isPrivate          = None,
        repoType           = Some(RepoType.Service),
        serviceType        = Some(ServiceType.Frontend),
        digitalServiceName = None,
        owningTeams        = None,
        teams              = None,
        prototypeName      = None
      )
