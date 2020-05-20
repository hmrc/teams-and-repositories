/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.teamsandrepositories.persitence.MongoTeamsAndRepositoriesPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

class MongoTeamsAndRepositoriesPersisterSpec
    extends AnyWordSpec
    with Matchers
    with LoneElement
    with MongoSpecSupport
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with GuiceOneAppPerSuite {

  override implicit val patienceConfig = PatienceConfig(timeout = 30.seconds, interval = 100.millis)

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[Module])
      .configure(Map(
        "mongodb.uri" -> "mongodb://localhost:27017/test-teams-and-repositories",
        "metrics.jvm" -> false)
      )
      .build()

  val mongoTeamsAndReposPersister = app.injector.instanceOf(classOf[MongoTeamsAndRepositoriesPersister])

  override def beforeEach() {
    mongoTeamsAndReposPersister.drop.futureValue
  }

  "getAllTeamAndRepos" should {
    "be able to add, get all teams and repos and delete everything... Everything!" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val gitRepository3 =
        GitRepository("repo-name3", "Desc3", "url3", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository4 =
        GitRepository("repo-name4", "Desc4", "url4", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2), System.currentTimeMillis())
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepository3, gitRepository4), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue
      mongoTeamsAndReposPersister.insert(teamAndRepositories2).futureValue

      val all = mongoTeamsAndReposPersister.getAllTeamAndRepos.futureValue

      all should contain theSameElementsAs Seq(teamAndRepositories1, teamAndRepositories2)
    }
  }

  "getTeamsAndRepos" should {
    "return a list of Teams and Repositories for a given list of service names" in {
      val gitRepository1 =
        GitRepository("Repo-Name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))
      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue

      val gitRepository3 =
        GitRepository("repo-name3", "Desc3", "url3", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepository3), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories2).futureValue

      val gitRepository4 =
        GitRepository("repo-name4", "Desc4", "url4", 3, 4, false, RepoType.Library, language = Some("Scala"))
      val teamAndRepositories3 =
        TeamRepositories("test-team2", List(gitRepository4), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories3).futureValue

      val result = mongoTeamsAndReposPersister.getTeamsAndRepos(Seq("repo-name1", "repo-name4")).futureValue

      result should contain theSameElementsAs List(teamAndRepositories1, teamAndRepositories3)
    }
  }

  "update" should {
    "update already existing team" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val teamAndRepositories1 = TeamRepositories("test-team", List(gitRepository1), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue

      val teamAndRepositories2 = TeamRepositories("test-team", List(gitRepository2), System.currentTimeMillis())
      mongoTeamsAndReposPersister.update(teamAndRepositories2).futureValue

      val allUpdated = mongoTeamsAndReposPersister.getAllTeamAndRepos.futureValue
      allUpdated.size shouldBe 1
      val updatedDeployment: TeamRepositories = allUpdated.loneElement

      updatedDeployment.teamName     shouldBe "test-team"
      updatedDeployment.repositories shouldBe List(gitRepository2)
    }
  }

  "deleteTeam" should {
    "remove all given teams" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val gitRepository3 =
        GitRepository("repo-name3", "Desc3", "url3", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository4 =
        GitRepository("repo-name4", "Desc4", "url4", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2), System.currentTimeMillis())
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepository3, gitRepository4), System.currentTimeMillis())
      val teamAndRepositories3 = TeamRepositories("test-team3", List(gitRepository1), System.currentTimeMillis())

      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue
      mongoTeamsAndReposPersister.insert(teamAndRepositories2).futureValue
      mongoTeamsAndReposPersister.insert(teamAndRepositories3).futureValue

      List("test-team1", "test-team2").foreach { teamName =>
        mongoTeamsAndReposPersister.deleteTeam(teamName).futureValue
      }

      val allRemainingTeams = mongoTeamsAndReposPersister.getAllTeamAndRepos.futureValue
      allRemainingTeams.size shouldBe 1

      allRemainingTeams shouldBe List(teamAndRepositories3)
    }
  }

  "resetLastActiveDate" should {

    "set repo's lastActiveDate to 0 and return 1 as number of modified records" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))
      val gitRepository3 =
        GitRepository("repo-name3", "Desc3", "url3", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2, gitRepository3), System.currentTimeMillis())

      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue

      mongoTeamsAndReposPersister.resetLastActiveDate(gitRepository2.name).futureValue shouldBe Some(1)

      val persistedTeam = mongoTeamsAndReposPersister.getAllTeamAndRepos.futureValue.head

      persistedTeam.repositories should contain theSameElementsAs Seq(
        gitRepository1,
        gitRepository2.copy(lastActiveDate = 0L),
        gitRepository3
      )
    }

    "set lastActiveDate to 0 of all repos with the given name and return number of modified records" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue

      val gitRepository2 =
        GitRepository("repo-name1", "Desc2", "url2", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepository2), System.currentTimeMillis())
      mongoTeamsAndReposPersister.insert(teamAndRepositories2).futureValue

      mongoTeamsAndReposPersister.resetLastActiveDate(gitRepository1.name).futureValue shouldBe Some(2)

      mongoTeamsAndReposPersister.getAllTeamAndRepos.futureValue should contain theSameElementsAs Seq(
        teamAndRepositories1.copy(repositories = List(gitRepository1.copy(lastActiveDate = 0L))),
        teamAndRepositories2.copy(repositories = List(gitRepository2.copy(lastActiveDate = 0L)))
      )
    }

    "do nothing if there is no repo with the given name" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1), System.currentTimeMillis())

      mongoTeamsAndReposPersister.insert(teamAndRepositories1).futureValue

      mongoTeamsAndReposPersister.resetLastActiveDate("non-exisiting-repo").futureValue shouldBe None

      mongoTeamsAndReposPersister.getAllTeamAndRepos.futureValue shouldBe Seq(teamAndRepositories1)
    }
  }
}
