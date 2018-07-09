package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, FunSpec, LoneElement, OptionValues}
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.teamsandrepositories.persitence.MongoTeamsAndRepositoriesPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.ExecutionContext.Implicits.global

class MongoTeamsAndRepositoriesPersisterSpec
    extends UnitSpec
    with LoneElement
    with MongoSpecSupport
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with OneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[Module])
      .configure(Map("mongodb.uri" -> "mongodb://localhost:27017/test-teams-and-repositories"))
      .build()

  val mongoTeamsAndReposPersister = app.injector.instanceOf(classOf[MongoTeamsAndRepositoriesPersister])

  override def beforeEach() {
    await(mongoTeamsAndReposPersister.drop)
  }

  "get all" should {
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
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories1))
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories2))

      val all = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)

      all.size shouldBe 2
      val result1: TeamRepositories = all(0)
      val result2: TeamRepositories = all(1)

      result1.teamName shouldBe "test-team1"
      result2.teamName shouldBe "test-team2"

      result1.repositories shouldBe List(gitRepository1, gitRepository2)
      result2.repositories shouldBe List(gitRepository3, gitRepository4)

      await(mongoTeamsAndReposPersister.clearAllData)
      val all2 = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)

      all2.size shouldBe 0
    }
  }

  "update" should {
    "update already existing team" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val teamAndRepositories1 = TeamRepositories("test-team", List(gitRepository1), System.currentTimeMillis())
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories1))

      val teamAndRepositories2 = TeamRepositories("test-team", List(gitRepository2), System.currentTimeMillis())
      await(mongoTeamsAndReposPersister.update(teamAndRepositories2))

      val allUpdated = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)
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

      await(mongoTeamsAndReposPersister.insert(teamAndRepositories1))
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories2))
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories3))

      List("test-team1", "test-team2").foreach { teamName =>
        await(mongoTeamsAndReposPersister.deleteTeam(teamName))
      }

      val allRemainingTeams = await(mongoTeamsAndReposPersister.getAllTeamAndRepos)
      allRemainingTeams.size shouldBe 1

      allRemainingTeams shouldBe List(teamAndRepositories3)
    }
  }

  "deleteRepo" should {

    "remove repo with the given name" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val gitRepository2 =
        GitRepository("repo-name2", "Desc2", "url2", 3, 4, false, RepoType.Library, language = Some("Scala"))
      val gitRepository3 =
        GitRepository("repo-name3", "Desc3", "url3", 3, 4, false, RepoType.Library, language = Some("Scala"))

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1, gitRepository2, gitRepository3), System.currentTimeMillis())

      await(mongoTeamsAndReposPersister.insert(teamAndRepositories1))

      await(mongoTeamsAndReposPersister.deleteRepo(gitRepository2.name)) shouldBe Some(gitRepository2.name)

      val persistedTeam = await(mongoTeamsAndReposPersister.getAllTeamAndRepos).head

      persistedTeam.repositories should contain theSameElementsAs Seq(gitRepository1, gitRepository3)
    }

    "do nothing if there is no repo with the given name" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))

      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1), System.currentTimeMillis())

      await(mongoTeamsAndReposPersister.insert(teamAndRepositories1))

      await(mongoTeamsAndReposPersister.deleteRepo("non-exisiting-repo")) shouldBe None

      await(mongoTeamsAndReposPersister.getAllTeamAndRepos) shouldBe Seq(teamAndRepositories1)
    }

    "throw an exception if there are more repos with the given name" in {
      val gitRepository1 =
        GitRepository("repo-name1", "Desc1", "url1", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val teamAndRepositories1 =
        TeamRepositories("test-team1", List(gitRepository1), System.currentTimeMillis())
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories1))

      val gitRepository2 =
        GitRepository("repo-name1", "Desc2", "url2", 1, 2, false, RepoType.Service, language = Some("Scala"))
      val teamAndRepositories2 =
        TeamRepositories("test-team2", List(gitRepository2), System.currentTimeMillis())
      await(mongoTeamsAndReposPersister.insert(teamAndRepositories2))

      a[RuntimeException] should be thrownBy await(mongoTeamsAndReposPersister.deleteRepo(gitRepository1.name))
    }
  }
}
