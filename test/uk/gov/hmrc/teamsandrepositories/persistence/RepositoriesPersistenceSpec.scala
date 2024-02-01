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
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.connectors.BranchProtection
import uk.gov.hmrc.teamsandrepositories.models.{RepoType, ServiceType, GitRepository}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class RepositoriesPersistenceSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[GitRepository]
     with OptionValues {

  override protected val repository = new RepositoriesPersistence(mongoComponent)

  override protected val checkIndexedQueries: Boolean =
    // we run unindexed queries
    false

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  private val repo1 =
    GitRepository(
      name                = "repo1",
      description         = "desc 1",
      url                 = "git/repo1",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = None,
      tags                = None,
      digitalServiceName  = None,
      owningTeams         = Nil,
      language            = None,
      isArchived          = false,
      defaultBranch       = "main",
      branchProtection    = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReview = true, requiresCommitSignatures = true)),
      isDeprecated        = false,
      teams               = List("team1", "team2"),
      prototypeName       = None
    )

  private val repo2 =
    GitRepository(
      name                = "repo2",
      description         = "desc 2",
      url                 = "git/repo2",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = None,
      tags                = None,
      digitalServiceName  = None,
      owningTeams         = List("team4", "team5"),
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teams               = List("team2", "team3"),
      prototypeName       = None
    )

  private val repo3 =
    GitRepository(
      name                = "repo3",
      description         = "desc 3",
      url                 = "git/repo3",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Prototype,
      serviceType         = None,
      tags                = None,
      digitalServiceName  = None,
      owningTeams         = Nil,
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teams               = List("team1","team2", "team3"),
      prototypeName       = Some("https://repo3.herokuapp.com")
    )

  private val repo4 =
    GitRepository(
      name                = "repo4",
      description         = "desc 4",
      url                 = "git/repo4",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = Some(ServiceType.Frontend),
      tags                = None,
      digitalServiceName  = None,
      owningTeams         = Nil,
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teams               = List("team2", "team3"),
      prototypeName       = None
    )

  private val repo5 =
    GitRepository(
      name                = "repo5",
      description         = "desc 5",
      url                 = "git/repo5",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = Some(ServiceType.Backend),
      tags                = None,
      digitalServiceName  = None,
      owningTeams         = Nil,
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teams               = List("team2", "team3"),
      prototypeName       = None
    )

  "find" must  {
    "find all repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find().futureValue
      results must contain (repo1)
      results must contain (repo2)
    }

    "exclude archived repos" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find(isArchived = Some(false)).futureValue
      results must contain (repo1)
      results must not contain (repo2)
    }

    "find repos with team write-access" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find(team = Some("team3")).futureValue
      results must contain only (repo2)
    }

    "find repos owned by team" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find(owningTeam = Some("team4")).futureValue
      results must contain only (repo2)
    }

    "find repos containing name" in {
      val foo = repo1.copy(name = "foo")
      val bar = repo1.copy(name = "bar")
      repository.collection.insertMany(Seq(repo1, repo2, foo, bar)).toFuture().futureValue
      val results = repository.find(name = Some("repo")).futureValue
      results must contain theSameElementsAs Seq(repo1, repo2)
    }

    "find repos by repo type" in {
      repository.collection.insertMany(Seq(repo1, repo2, repo3)).toFuture().futureValue
      val results = repository.find(repoType = Some(RepoType.Prototype)).futureValue
      results must contain only (repo3)
      val results2 = repository.find(repoType = Some(RepoType.Service)).futureValue
      results2 must contain theSameElementsAs Seq(repo1, repo2)

    }

    "find repos by service type" in {
      repository.collection.insertMany(Seq(repo3, repo4, repo5)).toFuture().futureValue
      val results = repository.find(serviceType = Some(ServiceType.Frontend)).futureValue
      results must contain only repo4
      val results2 = repository.find(serviceType = Some(ServiceType.Backend)).futureValue
      results2 must contain only repo5
    }
  }

  "findTeamSummaries" must {
    "return all the unique team names" in {
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.findTeamSummaries().futureValue
      results.map(_.name) must contain theSameElementsAs Seq("team1", "team2", "team3")
    }
  }

  "update" must {
    "insert new repositories" in {
      repository.updateRepos(Seq(repo1,repo2)).futureValue
      findAll().futureValue must contain theSameElementsAs Seq(repo1, repo2)
    }

    "update existing repositories" in {
      insert(repo1.copy(description = "the old description")).futureValue
      repository.updateRepos(Seq(repo1,repo2)).futureValue
      findAll().futureValue must contain theSameElementsAs Seq(repo1, repo2)
    }

    "delete repos not in the update list" in {
      insert(repo1).futureValue
      insert(repo2).futureValue
      repository.updateRepos(Seq(repo1)).futureValue
      findAll().futureValue must contain (repo1)
      findAll().futureValue must not contain repo2
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
       } yield ()
      ).futureValue
    }
  }

  "updateRepo" should {
    "insert a new repository" in {
      repository.putRepo(repo1).futureValue
      repository.putRepo(repo2).futureValue
      findAll().futureValue must contain theSameElementsAs Seq(repo1, repo2)
    }

    "update an existing repository" in {
      (for {
         _                        <- repository.putRepo(repo1)
         serviceTypeBefore        <- repository.findRepo(repo1.name).map(_.value.serviceType)
         expectedServiceTypeAfter =  ServiceType.Backend
         _                        <- repository.putRepo(repo1.copy(serviceType = Some(expectedServiceTypeAfter)))
         serviceTypeAfter         <- repository.findRepo(repo1.name).map(_.value.serviceType)
         _                        =  serviceTypeAfter mustNot be(serviceTypeBefore)
         _                        =  serviceTypeAfter mustBe Some(expectedServiceTypeAfter)
       } yield ()
      ).futureValue
    }
  }

  "archiveRepo" should {
    "set the isArchived flag to true" in {
      repository.putRepo(repo1).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(repo1)

      repository.archiveRepo(repo1.name).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(repo1.copy(isArchived = true))
    }
  }

  "deleteRepo" should {
    "delete the repository" in {
      repository.putRepo(repo1).futureValue
      repository.putRepo(repo2).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(repo1, repo2)

      repository.deleteRepo(repo1.name).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(repo2)
    }
  }
}
