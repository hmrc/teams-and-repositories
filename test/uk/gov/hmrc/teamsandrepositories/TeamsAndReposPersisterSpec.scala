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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoTeamsAndRepositoriesPersister, TeamsAndReposPersister}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


class TeamsAndReposPersisterSpec
    extends AnyWordSpec
    with Matchers
    with OptionValues
    with MockitoSugar
    with LoneElement
    with MongoSpecSupport
    with ScalaFutures
    with BeforeAndAfterEach
    with GuiceOneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .configure("metrics.jvm" -> false)
      .build()

  private val teamsAndReposPersister = mock[MongoTeamsAndRepositoriesPersister]

  val teamAndRepositories = TeamRepositories("teamX", Nil, System.currentTimeMillis())

  private val metrics: Metrics = new Metrics() {
    override def defaultRegistry = new MetricRegistry
    override def toJson          = ???
  }

  val persister = new TeamsAndReposPersister(teamsAndReposPersister, new FutureHelpers(metrics))

  "TeamsAndReposPersisterSpec" should {
    "delegate to teamsAndReposPersister's update" in {
      persister.update(teamAndRepositories)

      verify(teamsAndReposPersister).update(teamAndRepositories)
    }

    "get the teamRepos and update time together" in {
      when(teamsAndReposPersister.getAllTeamAndRepos)
        .thenReturn(Future.successful(List(teamAndRepositories)))

      val retVal = persister.getAllTeamsAndRepos

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
