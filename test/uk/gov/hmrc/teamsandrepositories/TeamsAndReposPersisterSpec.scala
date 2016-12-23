package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.Future

class TeamsAndReposPersisterSpec extends WordSpec with Matchers with OptionValues with MockitoSugar with LoneElement with MongoSpecSupport with ScalaFutures with BeforeAndAfterEach with OneAppPerSuite {


  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .build()

  private val teamsAndReposPersister = mock[MongoTeamsAndRepositoriesPersister]
  private val updateTimePersister = mock[MongoUpdateTimePersister]

  val teamAndRepositories = TeamRepositories("teamX", Nil)

  val persister = new TeamsAndReposPersister(teamsAndReposPersister, updateTimePersister)

  "TeamsAndReposPersisterSpec" should {
    "delegate to teamsAndReposPersister's update" in {

      persister.update(teamAndRepositories)

      verify(teamsAndReposPersister).update(teamAndRepositories)
    }

    "get the teamRepos and update time together" in {
      val now = LocalDateTime.now

      when(teamsAndReposPersister.getAllTeamAndRepos)
        .thenReturn(Future.successful(List(teamAndRepositories)))

      when(updateTimePersister.get(persister.teamsAndRepositoriesTimestampKeyName))
        .thenReturn(Future.successful(Some(KeyAndTimestamp(persister.teamsAndRepositoriesTimestampKeyName, now))))

      val retVal = persister.getAllTeamAndRepos

      retVal.futureValue._1 shouldBe Seq(teamAndRepositories)
      retVal.futureValue._2.value shouldBe now
    }

    "delegate to teamsAndReposPersister and updateTimePersister for clearAll" in {

      persister.clearAllData

      verify(teamsAndReposPersister, times(1)).clearAllData
      verify(updateTimePersister, times(1)).remove(persister.teamsAndRepositoriesTimestampKeyName)
    }

    "delegate to updateTimePersister for updating timestamp" in {
      val now = LocalDateTime.now

      persister.updateTimestamp(now)

      verify(updateTimePersister, times(1)).update(KeyAndTimestamp(persister.teamsAndRepositoriesTimestampKeyName, now))
    }

    "delegate to teamsAndReposPersister for removing a team in mongo" in {
      val now = LocalDateTime.now

      persister.deleteTeams(Set("team1", "team2"))

      verify(teamsAndReposPersister).deleteTeam("team1")
      verify(teamsAndReposPersister).deleteTeam("team2")
    }
  }
}