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

package uk.gov.hmrc.teamsandrepositories

import java.time.Instant

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.teamsandrepositories.persitence.MongoTeamsAndRepositoriesPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

class MongoTeamsAndRepositoriesPersisterSpec
    extends AnyWordSpec
    with Matchers
    with LoneElement
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with GuiceOneAppPerSuite {

  override implicit val patienceConfig = PatienceConfig(timeout = 30.seconds, interval = 100.millis)

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

  private val gitRepository1 =
    GitRepository("repo-name1", "Desc1", "url1", Instant.now(), Instant.now(), false, RepoType.Service, language = Some("Scala"), archived = false)
  private val gitRepository2 =
    GitRepository("repo-name2", "Desc2", "url2", Instant.now(), Instant.now(), false, RepoType.Library, language = Some("Scala"), archived = false)
  private val gitRepository3 =
    GitRepository("repo-name3", "Desc3", "url3", Instant.now(), Instant.now(), false, RepoType.Service, language = Some("Scala"), archived = false)
  private val gitRepository4 =
    GitRepository("repo-name4", "Desc4", "url4", Instant.now(), Instant.now(), false, RepoType.Library, language = Some("Scala"), archived = false)
  private val gitRepositoryArchived =
    GitRepository("repo-name-archived", "Desc5", "url5", Instant.now(), Instant.now(), false, RepoType.Service, language = Some("Scala"), archived = true)

  "getAllTeamAndRepos" should {
    val teamAndRepositories1 =
      TeamRepositories("test-team1", List(gitRepository1, gitRepository2), Instant.now())
    val teamAndRepositories2 =
      TeamRepositories("test-team2", List(gitRepository3, gitRepository4), Instant.now())
    val teamAndRepositories3 =
      TeamRepositories("test-team3", List(gitRepository4), Instant.now())

    "be able to add, get all teams and repos and delete everything... Everything!" in {
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue

      val all = mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue

      all should contain theSameElementsAs Seq(teamAndRepositories1, teamAndRepositories2)
    }

    "return only un-archived repositories when archived is false" in {
      val updateDate = Instant.now()
      val teamAndRepositoriesWithArchived =
        TeamRepositories("test-team1", List(gitRepository1, gitRepositoryArchived), updateDate)

      mongoTeamsAndReposPersister.update(teamAndRepositoriesWithArchived).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories3).futureValue

      val result = mongoTeamsAndReposPersister.getAllTeamAndRepos(Some(false)).futureValue
      result should contain theSameElementsAs List(
        TeamRepositories("test-team1", List(gitRepository1), updateDate),
        teamAndRepositories2,
        teamAndRepositories3
      )
    }

    "return only archived repositories when archived is true" in {
      val updateDate = Instant.now()
      val teamAndRepositoriesWithArchived =
        TeamRepositories("test-team1", List(gitRepository1, gitRepositoryArchived), updateDate)

      mongoTeamsAndReposPersister.update(teamAndRepositoriesWithArchived).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.update(teamAndRepositories3).futureValue

      val result = mongoTeamsAndReposPersister.getAllTeamAndRepos(Some(true)).futureValue
      result should contain theSameElementsAs List(
        TeamRepositories("test-team1", List(gitRepositoryArchived), updateDate),
        teamAndRepositories2.copy(repositories = List()),
        teamAndRepositories3.copy(repositories = List())
      )
    }
  }

  "getTeamsAndRepos" should {
    val teamAndRepositories1 =
      TeamRepositories("test-team1", List(gitRepository1, gitRepository2), Instant.now())
    val teamAndRepositories2 =
      TeamRepositories("test-team2", List(gitRepository3), Instant.now())
    val teamAndRepositories3 =
      TeamRepositories("test-team3", List(gitRepository4), Instant.now())

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
      val teamAndRepositories1 = TeamRepositories("test-team", List(gitRepository1), Instant.now())
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue

      val teamAndRepositories2 = TeamRepositories("test-team", List(gitRepository2), Instant.now())
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
      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2), Instant.now())
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepository3, gitRepository4), Instant.now())
      val teamAndRepositories3 =
        TeamRepositories("test-team3", List(gitRepository1), Instant.now())

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

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2, gitRepository3), Instant.now())

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
        GitRepository("repo-to-reset-name", "Desc1", "url1", Instant.now(), Instant.now(), false, RepoType.Service, language = Some("Scala"), archived = false)
      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepositoryToReset1), Instant.now())
      mongoTeamsAndReposPersister.update(teamAndRepositories1).futureValue

      val gitRepositoryToReset2 =
        GitRepository("repo-to-reset-name", "Desc2", "url2", Instant.now(), Instant.now(), false, RepoType.Service, language = Some("Scala"), archived = false)
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepositoryToReset2), Instant.now())
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue

      mongoTeamsAndReposPersister.resetLastActiveDate(gitRepositoryToReset1.name).futureValue shouldBe Some(2)

      mongoTeamsAndReposPersister.getAllTeamAndRepos(None).futureValue should contain theSameElementsAs Seq(
        teamAndRepositories1.copy(repositories = List(gitRepositoryToReset1.copy(lastActiveDate = Instant.ofEpochMilli(0)))),
        teamAndRepositories2.copy(repositories = List(gitRepositoryToReset2.copy(lastActiveDate = Instant.ofEpochMilli(0))))
      )
    }
  }
}
