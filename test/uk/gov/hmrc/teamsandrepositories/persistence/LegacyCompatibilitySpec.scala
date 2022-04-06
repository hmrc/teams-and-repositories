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
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.teamsandrepositories.models.RepoType.Service
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamName}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class LegacyCompatibilitySpec
  extends AnyWordSpecLike
  with Matchers
  with MockitoSugar
  with PlayMongoRepositorySupport[GitRepository]
  with CleanMongoCollectionSupport {

  override protected def repository = new RepositoriesPersistence(mongoComponent)

  val legacyPersistence = new RepositoriesPersistence(mongoComponent)

  private val repo1 = GitRepository("repo1", "desc 1", "git/repo1", Instant.now(), Instant.now(), isPrivate = false, Service, None, Nil, None, isArchived = false, "main", branchProtection = None, isDeprecated = false, List("team1"))
  private val repo2 = GitRepository("repo2", "desc 2", "git/repo2", Instant.now(), Instant.now(), isPrivate = false, Service, None, Nil, None, isArchived = true, "main", branchProtection = None, isDeprecated = false, List("team1"))

  "search" must {

    "find all repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = legacyPersistence.getAllTeamsAndRepos(None).futureValue
      results.length must be(1)
      results.head.repositories.map(_.name) must contain allOf("repo1", "repo2")
    }

    "show non-archived repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = legacyPersistence.getAllTeamsAndRepos(Some(false)).futureValue
      results.length must be(1)
      results.head.repositories.map(_.name) must contain only ("repo1")
    }

    "show only archived repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = legacyPersistence.getAllTeamsAndRepos(Some(true)).futureValue
      results.length must be(1)
      results.head.repositories.map(_.name) must contain only ("repo2")
    }
  }
}