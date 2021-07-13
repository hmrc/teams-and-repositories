/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.Instant

import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.controller.model.Team
import uk.gov.hmrc.teamsandrepositories.persistence.TeamsAndReposPersister

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TeamsControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with Results
     with OptionValues
     with GuiceOneServerPerSuite
     with Eventually {

  implicit val tf = Team.format

  private val now = Instant.now()

  private val createdDateForService1 = now.plusSeconds(1)
  private val createdDateForService2 = now.plusSeconds(2)
  private val createdDateForService3 = now.plusSeconds(3)
  private val createdDateForLib1     = now.plusSeconds(4)
  private val createdDateForLib2     = now.plusSeconds(5)

  private val lastActiveDateForService1 = now.plusSeconds(10)
  private val lastActiveDateForService2 = now.plusSeconds(20)
  private val lastActiveDateForService3 = now.plusSeconds(30)
  private val lastActiveDateForLib1     = now.plusSeconds(40)
  private val lastActiveDateForLib2     = now.plusSeconds(50)

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "github.open.api.host" -> "http://bla.bla",
          "github.open.api.user" -> "",
          "github.open.api.key"  -> "",
          "metrics.jvm"          -> false
        )
      )
      .build

  val defaultData =
    Seq(
      TeamRepositories(
        teamName      = "test-team",
        repositories = List(
          GitRepository(
            "repo-name",
            "some description",
            "repo-url",
            createdDate        = createdDateForService1,
            lastActiveDate     = lastActiveDateForService1,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          ),
          GitRepository(
            "library-repo",
            "some description",
            "library-url",
            createdDate        = createdDateForLib1,
            lastActiveDate     = lastActiveDateForLib1,
            repoType           = RepoType.Library,
            digitalServiceName = Some("digital-service-3"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate = Some(now),
        updateDate  = now
      ),
      TeamRepositories(
        teamName     = "another-team",
        repositories = List(
          GitRepository(
            "another-repo",
            "some description",
            "another-url",
            createdDate        = createdDateForService2,
            lastActiveDate     = lastActiveDateForService2,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          ),
          GitRepository(
            "middle-repo",
            "some description",
            "middle-url",
            createdDate        = createdDateForService3,
            lastActiveDate     = lastActiveDateForService3,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          ),
          GitRepository(
            "alibrary-repo",
            "some description",
            "library-url",
            createdDate        = createdDateForLib2,
            lastActiveDate     = lastActiveDateForLib2,
            repoType           = RepoType.Library,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          ),
          GitRepository(
            "CATO-prototype",
            "some description",
            "prototype-url",
            createdDate        = createdDateForLib2,
            lastActiveDate     = lastActiveDateForLib2,
            repoType           = RepoType.Prototype,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          ),
          GitRepository(
            "other-repo",
            "some description",
            "library-url",
            createdDate        = createdDateForLib2,
            lastActiveDate     = lastActiveDateForLib2,
            repoType           = RepoType.Other,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate = Some(now),
        updateDate  = now
      )
    )

  def singleRepoResult(teamName: String = "test-team", repoName: String = "repo-name", repoUrl: String = "repo-url") =
    Seq(
      TeamRepositories(
        teamName     = teamName,
        repositories = List(GitRepository(
          name           = repoName,
          description    = "some description",
          url            = repoUrl,
          createdDate    = now,
          lastActiveDate = now,
          repoType       = RepoType.Service,
          language       = Some("Scala"),
          isArchived     = false,
          defaultBranch  = "main"
        )),
        createdDate = Some(now),
        updateDate  = now
      )
    )

  "Teams controller" should {
    "have the correct url set up for the teams list" in {
      uk.gov.hmrc.teamsandrepositories.controller.routes.TeamsController.teams(includeRepos = true).url mustBe "/api/teams?includeRepos=true"
    }

    "have the correct url set up for a team's services" in {
      uk.gov.hmrc.teamsandrepositories.controller.routes.TeamsController
        .team("test-team", includeRepos = true)
        .url mustBe "/api/teams/test-team?includeRepos=true"
    }
  }

  "Retrieving a list of repositories for a team" should {
    "Return all repo types belonging to a team" in new Setup {
      val result = controller.team("another-team", includeRepos = true).apply(FakeRequest())

      val team = contentAsJson(result).as[Team]
      team.name mustBe "another-team"
      team.repos mustBe Some(Map(
        RepoType.Service   -> Seq("another-repo", "middle-repo"),
        RepoType.Library   -> Seq("alibrary-repo"),
        RepoType.Prototype -> Seq("CATO-prototype"),
        RepoType.Other     -> Seq("other-repo")
      ))
    }

    "Return information about all the teams that have access to a repo" in new Setup {
      val sourceData =
        Seq(
          TeamRepositories(
            teamName     = "test-team",
            repositories = List(
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                isArchived     = false,
                defaultBranch  = "main"
              )
            ),
            createdDate = Some(now),
            updateDate  = now
          ),
          TeamRepositories(
            teamName     = "another-team",
            repositories = List(
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                isArchived     = false,
                defaultBranch  = "main"
              )
            ),
            createdDate = Some(now),
            updateDate  = now
          )
        )

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(sourceData))

      val result = controller.team("another-team", includeRepos = true).apply(FakeRequest())

      contentAsJson(result).as[Team].repos mustBe Some(Map(
        RepoType.Service   -> List("repo-name"),
        RepoType.Library   -> List(),
        RepoType.Prototype -> List(),
        RepoType.Other     -> List()
      ))
    }
  }

  "Retrieving a list of repository details for a team" should {
    "Return all repo types belonging to a team" in new Setup {
      val result = controller.team("another-team", includeRepos = true).apply(FakeRequest())

      val team = contentAsJson(result).as[Team]

      team.repos.value mustBe Map(
        RepoType.Service   -> List("another-repo", "middle-repo"),
        RepoType.Library   -> List("alibrary-repo"),
        RepoType.Prototype -> List("CATO-prototype"),
        RepoType.Other     -> List("other-repo")
      )
    }

    "Return the repository information for the specified team" in new Setup {
      val sourceData =
        Seq(
          TeamRepositories(
            teamName     = "test-team",
            repositories = List(
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                isArchived     = false,
                defaultBranch  = "main"
              )
            ),
            createdDate = Some(now),
            updateDate  = now
          ),
          TeamRepositories(
            teamName     = "another-team",
            repositories = List(
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                isArchived     = false,
                defaultBranch  = "main"
              )
            ),
            createdDate = Some(now),
            updateDate  = now
          )
        )

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(sourceData))

      val result = controller.team("another-team", includeRepos = true).apply(FakeRequest())

      contentAsJson(result)
        .as[Team]
        .repos
        .value mustBe Map(
          RepoType.Service   -> List("repo-name"),
          RepoType.Library   -> List(),
          RepoType.Prototype -> List(),
          RepoType.Other     -> List()
        )
    }
  }

  "Returning a list of repositories by team" should {
    "return the empty list for repository type if a team does not have it" in new Setup {
      val sourceData =
        Seq(
          TeamRepositories(
            teamName     = "test-team",
            repositories = List(
              GitRepository(
                name           = "repo-open-name",
                description    = "some description",
                url            = "repo-open-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Library,
                language       = Some("Scala"),
                isArchived     = false,
                defaultBranch  = "main"
              )
            ),
            createdDate = Some(now),
            updateDate  = now
          )
        )

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(sourceData))

      val result = controller.team("test-team", includeRepos = true).apply(FakeRequest())

      val team = contentAsJson(result).as[Team]
      team.repos.map(_.get(RepoType.Service)) mustBe Some(Some(List()))
    }

    "return an empty list if a team has no repositories" in new Setup {
      val sourceData = Seq(
        TeamRepositories(
          teamName = "test-team",
          repositories = List(),
          createdDate = Some(now),
          updateDate  = now
        )
      )

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(sourceData))

      val result = controller.team("test-team", includeRepos = true).apply(FakeRequest())

      val team = contentAsJson(result).as[Team]
      team.repos mustBe Some(Map(
        RepoType.Service   -> List(),
        RepoType.Library   -> List(),
        RepoType.Prototype -> List(),
        RepoType.Other     -> List()
      ))
    }

    "return a 404 if a team does not exist at all" in new Setup {
      val sourceData = Seq.empty[TeamRepositories]

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(sourceData))

      val result = controller.team("test-team", includeRepos = true).apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  private trait Setup {
    val mockTeamsAndReposPersister = mock[TeamsAndReposPersister]
    val mockConfiguration          = mock[Configuration]

    when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
      .thenReturn(Future.successful(defaultData))

    when(mockConfiguration.get[Seq[String]]("shared.repositories"))
      .thenReturn(List.empty)

    val controller = new TeamsController(
      mockTeamsAndReposPersister,
      mockConfiguration,
      stubControllerComponents()
    )
  }
}
