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
import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport, PlayMongoRepositorySupport}
import uk.gov.hmrc.teamsandrepositories.connectors.BranchProtection
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamName}
import uk.gov.hmrc.teamsandrepositories.models.RepoType.{Prototype, Service}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class RepositoriesPersistenceSpec
  extends AnyWordSpecLike
  with Matchers
  with MockitoSugar
  with PlayMongoRepositorySupport[GitRepository]
  with CleanMongoCollectionSupport
  with OptionValues {

  override protected def repository = new RepositoriesPersistence(mongoComponent)

  private val repo1 =
    GitRepository(
      "repo1",
      "desc 1",
      "git/repo1",
      Instant.now(),
      Instant.now(),
      isPrivate = false,
      Service,
      None,
      Nil,
      None,
      isArchived = false,
      "main",
      branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReview = true, requiresCommitSignatures = true)),
      isDeprecated = false,
      List("team1", "team2")
    )

  private val repo2 =
    GitRepository(
      "repo2",
      "desc 2",
      "git/repo2",
      Instant.now(),
      Instant.now(),
      isPrivate = false,
      Service,
      None,
      Nil,
      None,
      isArchived = true,
      "main",
      branchProtection = None,
      isDeprecated = false,
      List("team2", "team3")
    )

  private val repo3 =
    GitRepository(
      "repo3",
      "desc 3",
      "git/repo3",
      Instant.now(),
      Instant.now(),
      isPrivate = false,
      Prototype,
      None,
      Nil,
      None,
      isArchived = true,
      "main",
      branchProtection = None,
      isDeprecated = false,
      List("team1","team2", "team3")
    )

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
      results must contain only (repo2)
    }

    "find repos by type" in {
      repository.collection.insertMany(Seq(repo1, repo2, repo3)).toFuture().futureValue
      val results = repository.search(repoType = Some(Prototype)).futureValue
      results must contain only (repo3)
      val results2 = repository.search(repoType = Some(Service)).futureValue
      results2 must contain only (repo1, repo2)

    }
  }

  "findTeamNames" must {
    "return all the unique team names" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.findTeamNames().futureValue
      results.map(_.name) must contain allOf("team1", "team2", "team3")
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

  "updateRepoBranchProtection" should {
    "update the branch protection policy of the given repository" in {
      (for {
        _               <- insert(repo1)
        bpBefore        <- repository.findRepo(repo1.name).map(_.value.branchProtection)
        expectedBpAfter =  BranchProtection(false, false, false)
        _               <- repository.updateRepoBranchProtection(repo1.name, Some(expectedBpAfter))
        bpAfter         <- repository.findRepo(repo1.name).map(_.value.branchProtection)
        _               =  bpAfter mustNot be(bpBefore)
        _               =  bpAfter mustBe Some(expectedBpAfter)
      } yield ()).futureValue
    }
  }
}