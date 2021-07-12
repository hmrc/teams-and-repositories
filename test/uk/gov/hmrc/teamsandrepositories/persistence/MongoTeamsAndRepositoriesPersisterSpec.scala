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

package uk.gov.hmrc.teamsandrepositories.persistence

import java.time.Instant

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.teamsandrepositories.{GitRepository, Module, RepoType, TeamRepositories}

import scala.concurrent.ExecutionContext.Implicits.global

class MongoTeamsAndRepositoriesPersisterSpec
  extends AnyWordSpec
     with Matchers
     with LoneElement
     with ScalaFutures
     with IntegrationPatience
     with OptionValues
     with BeforeAndAfterEach
     with GuiceOneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[Module])
      .configure(
        Map(
          "mongodb.uri" -> "mongodb://localhost:27017/test-teams-and-repositories",
          "metrics.jvm" -> false
        )
      )
      .build()

  val mongoTeamsAndReposPersister = app.injector.instanceOf(classOf[MongoTeamsAndRepositoriesPersister])

  override def beforeEach() {
    mongoTeamsAndReposPersister.clearAllData.futureValue
  }

  private val teamCreatedDate = Instant.parse("2019-04-01T12:00:00Z")

  private val gitRepository1 =
    GitRepository(
      name           = "repo-name1",
      description    = "Desc1",
      url            = "url1",
      createdDate    = Instant.now(),
      lastActiveDate = Instant.now(),
      isPrivate      = false,
      repoType       = RepoType.Service,
      language       = Some("Scala"),
      isArchived     = false,
      defaultBranch  = "main"
    )
  private val gitRepository2 =
    GitRepository(
      name           = "repo-name2",
      description    = "Desc2",
      url            = "url2",
      createdDate    = Instant.now(),
      lastActiveDate = Instant.now(),
      isPrivate      = false,
      repoType       = RepoType.Library,
      language       = Some("Scala"),
      isArchived     = false,
      defaultBranch  = "main"
    )
  private val gitRepository3 =
    GitRepository(
      name           = "repo-name3",
      description    = "Desc3",
      url            = "url3",
      createdDate    = Instant.now(),
      lastActiveDate = Instant.now(),
      isPrivate      = false,
      repoType       = RepoType.Service,
      language       = Some("Scala"),
      isArchived     = false,
      defaultBranch  = "main"
    )
  private val gitRepository4 =
    GitRepository(
      name           = "repo-name4",
      description    = "Desc4",
      url            = "url4",
      createdDate    = Instant.now(),
      lastActiveDate = Instant.now(),
      isPrivate      = false,
      repoType       = RepoType.Library,
      language       = Some("Scala"),
      isArchived     = false,
      defaultBranch  = "main"
    )
  private val gitRepositoryArchived =
    GitRepository(
      name           = "repo-name-archived",
      description    = "Desc5",
      url            = "url5",
      createdDate    = Instant.now(),
      lastActiveDate = Instant.now(),
      isPrivate      = false,
      repoType       = RepoType.Service,
      language       = Some("Scala"),
      isArchived     = true,
      defaultBranch  = "main"
    )

  "getAllTeamAndRepos" should {
    val teamAndRepositories1 = buildTeamsRepositories("test-team1", List(gitRepository1, gitRepository2))
    val teamAndRepositories2 = buildTeamsRepositories("test-team2", List(gitRepository3, gitRepository4))
    val teamAndRepositories3 = buildTeamsRepositories("test-team3", List(gitRepository4))

    "be able to add, get all teams and repos and delete everything... Everything!" in {
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue

      val all = mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue

      all should contain theSameElementsAs Seq(teamAndRepositories1, teamAndRepositories2)
    }

    "return only un-archived repositories when archived is false" in {
      val teamAndRepositoriesWithArchived = buildTeamsRepositories("test-team1", List(gitRepository1, gitRepositoryArchived))

      mongoTeamsAndReposPersister.update(teamAndRepositoriesWithArchived).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories3).futureValue

      val result = mongoTeamsAndReposPersister.getAllTeamAndRepos(Some(false)).futureValue
      result should contain theSameElementsAs List(
        teamAndRepositoriesWithArchived.copy(repositories = List(gitRepository1)),
        teamAndRepositories2,
        teamAndRepositories3
      )
    }

    "return only archived repositories when archived is true" in {
      val teamAndRepositoriesWithArchived = buildTeamsRepositories("test-team1", List(gitRepository1, gitRepositoryArchived))

      mongoTeamsAndReposPersister.update(teamAndRepositoriesWithArchived).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories3).futureValue

      val result = mongoTeamsAndReposPersister.getAllTeamAndRepos(Some(true)).futureValue
      result should contain theSameElementsAs List(
        teamAndRepositoriesWithArchived.copy(repositories = List(gitRepositoryArchived)),
        teamAndRepositories2.copy(repositories = List()),
        teamAndRepositories3.copy(repositories = List())
      )
    }
  }

  "getTeamsAndRepos" should {
    val teamAndRepositories1 = buildTeamsRepositories("test-team1", List(gitRepository1, gitRepository2))
    val teamAndRepositories2 = buildTeamsRepositories("test-team2", List(gitRepository3))
    val teamAndRepositories3 = buildTeamsRepositories("test-team3", List(gitRepository4))

    "return a list of Teams and Repositories for a given list of service names" in {
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories3).futureValue

      val result = mongoTeamsAndReposPersister.getTeamsAndRepos(Seq("repo-name1", "repo-name4")).futureValue
      result should contain theSameElementsAs List(teamAndRepositories1, teamAndRepositories3)
    }
  }

  "update" should {
    "update already existing team" in {
      val teamAndRepositories1 = buildTeamsRepositories("test-team", List(gitRepository1))
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue

      val teamAndRepositories2 = buildTeamsRepositories("test-team", List(gitRepository2))
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue

      val allUpdated = mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue
      allUpdated.size shouldBe 1
      val updatedDeployment: TeamRepositories = allUpdated.loneElement

      updatedDeployment.teamName     shouldBe "test-team"
      updatedDeployment.repositories shouldBe List(gitRepository2)
    }
  }

  "deleteTeam" should {
    "remove all given teams" in {
      val teamAndRepositories1 = buildTeamsRepositories("test-team1", List(gitRepository1, gitRepository2))
      val teamAndRepositories2 = buildTeamsRepositories("test-team2", List(gitRepository3, gitRepository4))
      val teamAndRepositories3 = buildTeamsRepositories("test-team3", List(gitRepository1))

      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories3).futureValue

      List("test-team1", "test-team2").foreach { teamName =>
        mongoTeamsAndReposPersister.deleteTeam(teamName).futureValue
      }

      val allRemainingTeams = mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue
      allRemainingTeams.size shouldBe 1

      allRemainingTeams shouldBe List(teamAndRepositories3)
    }
  }

  "resetLastActiveDate" should {
    "set repo's lastActiveDate to 0 and return 1 as number of modified records" in {
      val teamAndRepositories1 = buildTeamsRepositories("test-team1", List(gitRepository1, gitRepository2, gitRepository3))

      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue

      mongoTeamsAndReposPersister.resetLastActiveDate(gitRepository2.name).futureValue shouldBe Some(1)

      val persistedTeam = mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue.head

      persistedTeam.repositories should contain theSameElementsAs Seq(
        gitRepository1,
        gitRepository2.copy(lastActiveDate = Instant.ofEpochMilli(0)),
        gitRepository3
      )
    }

    "set lastActiveDate to 0 of all repos with the given name and return number of modified records" in {
      val gitRepositoryToReset1 =
        GitRepository(
          "repo-to-reset-name",
          "Desc1",
          "url1",
          Instant.now(),
          Instant.now(),
          false,
          RepoType.Service,
          language      = Some("Scala"),
          isArchived    = false,
          defaultBranch = "main"
        )
      val teamAndRepositories1 = buildTeamsRepositories("test-team1", List(gitRepositoryToReset1))
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue

      val gitRepositoryToReset2 =
        GitRepository(
          "repo-to-reset-name",
          "Desc2",
          "url2",
          Instant.now(),
          Instant.now(),
          false,
          RepoType.Service,
          language      = Some("Scala"),
          isArchived    = false,
          defaultBranch = "main"
        )
      val teamAndRepositories2 = buildTeamsRepositories("test-team2", List(gitRepositoryToReset2))
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue

      mongoTeamsAndReposPersister.resetLastActiveDate(gitRepositoryToReset1.name).futureValue shouldBe Some(2)

      mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue should contain theSameElementsAs Seq(
        teamAndRepositories1.copy(repositories = List(gitRepositoryToReset1.copy(lastActiveDate = Instant.ofEpochMilli(0)))),
        teamAndRepositories2.copy(repositories = List(gitRepositoryToReset2.copy(lastActiveDate = Instant.ofEpochMilli(0))))
      )
    }
  }

  def buildTeamsRepositories(teamName: String, repos: List[GitRepository]): TeamRepositories =
    TeamRepositories(
      teamName     = teamName,
      repositories = repos,
      createdDate  = Some(teamCreatedDate),
      updateDate   = Instant.now()
    )
}
