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

import org.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamName}
import uk.gov.hmrc.teamsandrepositories.models.RepoType.Service

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class RepositoriesPersistenceSpec
  extends AnyWordSpecLike
  with Matchers
  with MockitoSugar
  with PlayMongoRepositorySupport[GitRepository]
  with CleanMongoCollectionSupport {

  override protected def repository = new RepositoriesPersistence(mongoComponent)

  private val repo1 = GitRepository("repo1", "desc 1", "git/repo1", Instant.now(), Instant.now(), isPrivate = false, Service, None, Nil, None, isArchived = false, "main", isDeprecated = false, List("team1", "team2"))
  private val repo2 = GitRepository("repo2", "desc 2", "git/repo2", Instant.now(), Instant.now(), isPrivate = false, Service, None, Nil, None, isArchived = true, "main", isDeprecated = false, List("team2", "team3"))

  "search" must  {

    "find all repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.search().futureValue
      results must contain (repo1)
      results must contain (repo2)
    }

    "exclude archived repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.search(isArchived = Some(false)).futureValue
      results must contain (repo1)
      results must not contain (repo2)
    }

    "find repos belonging to a team" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.search(team = Some("team3")).futureValue
      results must contain (repo2)
      results must not contain (repo1)
    }
  }

  "findTeamNames" must {
    "return all the unique team names" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.findTeamNames().futureValue
      results must contain allOf(TeamName("team1"), TeamName("team2"), TeamName("team3"))
    }
  }

  "update" must {
    "insert new repositories" in {
      repository.updateRepos(Seq(repo1,repo2)).futureValue
      findAll().futureValue must contain allOf(repo1, repo2)
    }

    "update existing repositories" in {
      insert(repo1.copy(description = "the old description")).futureValue
      repository.updateRepos(Seq(repo1,repo2)).futureValue
      findAll().futureValue must contain allOf(repo1, repo2)
    }

    "delete repos not in the update list" in {
      insert(repo1).futureValue
      insert(repo2).futureValue
      repository.updateRepos(Seq(repo1)).futureValue
      findAll().futureValue must contain (repo1)
      findAll().futureValue must not contain (repo2)

    }
  }
}