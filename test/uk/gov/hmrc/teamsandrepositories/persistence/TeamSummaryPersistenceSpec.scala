/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.models.TeamSummary

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class TeamSummaryPersistenceSpec
  extends AnyWordSpecLike
    with Matchers
    with MockitoSugar
    with DefaultPlayMongoRepositorySupport[TeamSummary] {

  override protected val repository = new TeamSummaryPersistence(mongoComponent)

  override protected val checkIndexedQueries: Boolean =
  // we run unindexed queries
    false

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  private val teamSummary1 =
    TeamSummary(
      name           = "team-one",
      lastActiveDate = Some(now),
      repos          = Seq("repo-one")
    )

  private val teamSummary2 =
    TeamSummary(
      name           = "team-two",
      lastActiveDate = Some(now),
      repos          = Seq("repo-one")
    )

  "update" must {
    "insert new team summaries" in {
      repository.updateTeamSummaries(List(teamSummary1, teamSummary2)).futureValue
      findAll().futureValue must contain theSameElementsAs List(teamSummary1, teamSummary2)
    }

    "update existing teams summaries" in {
      insert(teamSummary1.copy(lastActiveDate = Some(now.minus(5, ChronoUnit.DAYS)))).futureValue
      repository.updateTeamSummaries(List(teamSummary1, teamSummary2)).futureValue
      findAll().futureValue must contain theSameElementsAs List(teamSummary1, teamSummary2)
    }

    "delete team summaries not in the update list" in {
      insert(teamSummary1).futureValue
      insert(teamSummary2).futureValue
      repository.updateTeamSummaries(List(teamSummary1)).futureValue
      findAll().futureValue must contain (teamSummary1)
      findAll().futureValue must not contain teamSummary2
    }
  }

  "findTeamSummaries" must {
    "return all summaries for all teams" in {
      insert(teamSummary1).futureValue
      insert(teamSummary2).futureValue
      repository.findTeamSummaries().futureValue must contain theSameElementsAs Seq(teamSummary1, teamSummary2)
    }
  }
}
