package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.teamsandrepositories.persitence.model.{KeyAndTimestamp, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoTeamsAndRepositoriesPersister, TeamsAndReposPersister}

import scala.concurrent.Future

class TeamsAndReposPersisterSpec
    extends WordSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with LoneElement
    with MongoSpecSupport
    with ScalaFutures
    with BeforeAndAfterEach
    with OneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .build()

  private val teamsAndReposPersister = mock[MongoTeamsAndRepositoriesPersister]

  val teamAndRepositories = TeamRepositories("teamX", Nil, System.currentTimeMillis())

  val persister = new TeamsAndReposPersister(teamsAndReposPersister)

  "TeamsAndReposPersisterSpec" should {
    "delegate to teamsAndReposPersister's update" in {
      persister.update(teamAndRepositories)

      verify(teamsAndReposPersister).update(teamAndRepositories)
    }

    "get the teamRepos and update time together" in {
      when(teamsAndReposPersister.getAllTeamAndRepos)
        .thenReturn(Future.successful(List(teamAndRepositories)))

      val retVal = persister.getAllTeamAndRepos

      retVal.futureValue shouldBe Seq(teamAndRepositories)
    }

    "delegate to teamsAndReposPersister for clearAll" in {
      persister.clearAllData

      verify(teamsAndReposPersister, times(1)).clearAllData
    }

    "delegate to teamsAndReposPersister for removing a team in mongo" in {
      persister.deleteTeams(Set("team1", "team2"))

      verify(teamsAndReposPersister).deleteTeam("team1")
      verify(teamsAndReposPersister).deleteTeam("team2")
    }
  }
}
