package uk.gov.hmrc.teamsandrepositories.controller

import org.mockito.MockitoSugar
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
import uk.gov.hmrc.teamsandrepositories.models.{DeletedGitRepository, GitRepository, RepoType, ServiceType}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence}

import java.time.Instant
import scala.concurrent.Future

class TeamsAndRepositoriesControllerSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with BeforeAndAfterEach {

  val mockDeletedRepositoriesPersistence: DeletedRepositoriesPersistence = mock[DeletedRepositoriesPersistence]
  val mockRepositoriesPersistence       : RepositoriesPersistence        = mock[RepositoriesPersistence]

  implicit override lazy val app: Application = {
    new GuiceApplicationBuilder()
      .overrides(
        bind[DeletedRepositoriesPersistence].toInstance(mockDeletedRepositoriesPersistence),
        bind[RepositoriesPersistence].toInstance(mockRepositoriesPersistence)
      )
      .build()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDeletedRepositoriesPersistence)
    reset(mockRepositoriesPersistence)
  }

  private def getRoute: String = v2.routes.TeamsAndRepositoriesController.decommissionedServices().url

  "TeamsAndRepositoriesController" should {
    "return all decommissioned services" in {

      val repoTypeService = Some(RepoType.Service)

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

      val result = route(app, FakeRequest(GET, getRoute)).value

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
