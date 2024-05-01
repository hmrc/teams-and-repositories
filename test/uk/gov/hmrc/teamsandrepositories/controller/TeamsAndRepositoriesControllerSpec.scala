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

import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.internalauth.client.BackendAuthComponents
import uk.gov.hmrc.teamsandrepositories.controller.v2.TeamsAndRepositoriesController
import uk.gov.hmrc.teamsandrepositories.models.{DeletedGitRepository, GitRepository, RepoType, ServiceType}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence, TeamSummaryPersistence}
import uk.gov.hmrc.teamsandrepositories.services.BranchProtectionService

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndRepositoriesControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with OptionValues {

  val mockDeletedRepositoriesPersistence: DeletedRepositoriesPersistence = mock[DeletedRepositoriesPersistence]
  val mockRepositoriesPersistence       : RepositoriesPersistence        = mock[RepositoriesPersistence]
  val mockBranchProtectionService       : BranchProtectionService        = mock[BranchProtectionService]
  val mockTeamSummaryPersistence        : TeamSummaryPersistence         = mock[TeamSummaryPersistence]
  val mockAuthComponents                : BackendAuthComponents          = mock[BackendAuthComponents]



  "TeamsAndRepositoriesController" should {
    "return all decommissioned services" in {

      val repoTypeService = Some(RepoType.Service)
      val controller      = new TeamsAndRepositoriesController(
        mockRepositoriesPersistence,
        mockTeamSummaryPersistence,
        mockDeletedRepositoriesPersistence,
        mockBranchProtectionService,
        mockAuthComponents,
        stubControllerComponents()
      )

      when(mockRepositoriesPersistence.find(isArchived = Some(true), repoType = repoTypeService))
        .thenReturn(Future.successful(Seq(
          aRepo.copy(name = "service-1"),
          aRepo.copy(name = "service-2")
        )))

      when(mockDeletedRepositoriesPersistence.find(repoType = repoTypeService))
        .thenReturn(Future.successful(Seq(
          aDeletedRepo.copy(name = "service-3"),
          aDeletedRepo.copy(name = "service-4")
        )))


      val result = controller.decommissionedServices().apply(FakeRequest())

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.parse(
        s"""
           |[
           |  {
           |    "repoName": "service-1"
           |  },
           |  {
           |    "repoName": "service-2"
           |   },
           |  {
           |    "repoName": "service-3"
           |  },
           |  {
           |    "repoName": "service-4"
           |  }
           |]
           |""".stripMargin)
    }
  }

  private lazy val now = Instant.now()

  private val aRepo =
    GitRepository(
      name                 = "",
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
      teams                = List.empty,
      prototypeName        = None,
      prototypeAutoPublish = None,
      repositoryYamlText   = None
  )

  private val aDeletedRepo =
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
}
