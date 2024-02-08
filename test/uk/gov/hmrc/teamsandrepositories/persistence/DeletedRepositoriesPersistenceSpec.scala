/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.models.{DeletedGitRepository, RepoType}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class DeletedRepositoriesPersistenceSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[DeletedGitRepository]
     with OptionValues {

    override protected val repository = new DeletedRepositoriesPersistence(mongoComponent)

    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

    private val repo1 =
      DeletedGitRepository(
        name                = "repo1",
        deletedDate         = now,
        isPrivate           = Some(false),
        repoType            = Some(RepoType.Service),
        serviceType         = None,
        digitalServiceName  = None,
        owningTeams         = Some(Nil),
        teams               = Some(List("team1", "team2")),
        prototypeName       = None
      )

    private val repo2 =
      DeletedGitRepository(
        name                = "repo2",
        deletedDate         = now,
        isPrivate           = Some(false),
        repoType            = Some(RepoType.Service),
        serviceType         = None,
        digitalServiceName  = None,
        owningTeams         = Some(List("team4", "team5")),
        teams               = Some(List("team2", "team3")),
        prototypeName       = None
      )

    "get" must  {
      "get all repos" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find(None, None).futureValue
        results mustBe Seq(repo1, repo2)
      }

      "get repo by name" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find(Some(repo1.name), None).futureValue
        results mustBe Seq(repo1)
      }

      "get repo by team" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find(None, repo2.owningTeams.map(_.head)).futureValue
        results mustBe Seq(repo2)
      }
  }

  "set" must {
    "insert repo" in {
      repository.set(Seq(repo1)).futureValue
      val results = repository.find(None, None).futureValue
      results mustBe Seq(repo1)
    }
  }
}
