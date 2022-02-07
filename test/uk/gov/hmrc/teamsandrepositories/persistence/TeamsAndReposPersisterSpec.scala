/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.teamsandrepositories.{Module, TeamRepositories}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndReposPersisterSpec
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with LoneElement
    with ScalaFutures
    with BeforeAndAfterEach
    with GuiceOneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .configure("metrics.jvm" -> false)
      .build()

  private val mongoTeamsAndRepositoriesPersister = mock[MongoTeamsAndRepositoriesPersister]

  val teamAndRepositories = TeamRepositories(
    teamName     = "teamX",
    repositories = Nil,
    createdDate  = Some(Instant.now()),
    updateDate   = Instant.now()
  )

  val persister = new TeamsAndReposPersister(mongoTeamsAndRepositoriesPersister)

  "TeamsAndReposPersister" should {
    "delegate to MongoTeamsAndReposPersister's update" in {
      persister.update(teamAndRepositories)

      verify(mongoTeamsAndRepositoriesPersister).update(teamAndRepositories)
    }

    "get the teamRepos and update time together" in {
      when(mongoTeamsAndRepositoriesPersister.getAllTeamAndRepos(None))
        .thenReturn(Future.successful(List(teamAndRepositories)))

      val retVal = persister.getAllTeamsAndRepos(None)

      retVal.futureValue shouldBe Seq(teamAndRepositories)
    }

    "delegate to MongoTeamsAndReposPersister for clearAll" in {
      persister.clearAllData

      verify(mongoTeamsAndRepositoriesPersister, times(1)).clearAllData
    }

    "delegate to MongoTeamsAndReposPersister for removing a team in mongo" in {
      persister.deleteTeams(Set("team1", "team2"))

      verify(mongoTeamsAndRepositoriesPersister).deleteTeam("team1")
      verify(mongoTeamsAndRepositoriesPersister).deleteTeam("team2")
    }
  }
}
