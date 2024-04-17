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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.models.{DeletedGitRepository, RepoType}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class DeletedRepositoriesPersistenceSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[DeletedGitRepository] {

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
        repoType            = Some(RepoType.Library),
        serviceType         = None,
        digitalServiceName  = None,
        owningTeams         = Some(List("team4", "team5")),
        teams               = Some(List("team2", "team3")),
        prototypeName       = None
      )

    "find" should  {
      "get all repos" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find().futureValue
        results shouldBe Seq(repo1, repo2)
      }

      "get repo by name" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find(name = Some(repo1.name)).futureValue
        results shouldBe Seq(repo1)
      }

      "get repo by team" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find(team = repo2.owningTeams.flatMap(_.headOption)).futureValue
        results shouldBe Seq(repo2)
      }

      "get all services" in {
        repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
        val results = repository.find(repoType = Some(RepoType.Service)).futureValue
        results shouldBe Seq(repo1)
      }
  }

  "putRepo" should {
    "insert repo" in {
      repository.putRepo(repo1).futureValue
      val results = repository.find().futureValue
      results shouldBe Seq(repo1)
    }

    "fail when inserting a duplicate" in {
      repository.putRepo(repo2).futureValue

      val result = repository.putRepo(repo2).failed.futureValue

      result shouldBe a[com.mongodb.MongoWriteException]
      result.getMessage should include("duplicate key error")
    }
  }

  "deleteRepos" should {
    "delete repo1" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      repository.deleteRepos(Seq(repo1.name)).futureValue shouldBe 1
      repository.find().futureValue                       shouldBe Seq(repo2)
    }

    "delete repo1 & repo2" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      repository.deleteRepos(Seq(repo1.name, repo2.name)).futureValue shouldBe 2
      repository.find().futureValue                                   shouldBe Seq.empty
    }

    "handle repo not found" in {
      repository.deleteRepos(Seq(repo1.name)).futureValue shouldBe 0
      repository.find().futureValue                       shouldBe Seq.empty
    }

    "handle Nil" in {
      repository.deleteRepos(Seq.empty).futureValue shouldBe 0
      repository.find().futureValue                 shouldBe Seq.empty
    }
  }
}
