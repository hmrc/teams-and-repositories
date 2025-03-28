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

import org.mongodb.scala.ObservableFuture
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.connector.BranchProtection
import uk.gov.hmrc.teamsandrepositories.model.{Organisation, RepoType, ServiceType, GitRepository}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class RepositoriesPersistenceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[GitRepository]
     with OptionValues:

  override protected val repository: RepositoriesPersistence = RepositoriesPersistence(mongoComponent)

  override protected val checkIndexedQueries: Boolean =
    // we run unindexed queries
    false

  private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  private val repo1 =
    GitRepository(
      name                = "repo1",
      organisation        = Some(Organisation.Mdtp),
      description         = "desc 1",
      url                 = "git/repo1",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = None,
      tags                = None,
      digitalServiceName  = Some("Service B"),
      owningTeams         = Nil,
      language            = None,
      isArchived          = false,
      defaultBranch       = "main",
      branchProtection    = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReview = true, requiresCommitSignatures = true)),
      isDeprecated        = false,
      teamNames           = List("team1", "team2"),
      prototypeName       = None
    )

  private val repo2 =
    GitRepository(
      name                = "repo2",
      organisation        = Some(Organisation.Mdtp),
      description         = "desc 2",
      url                 = "git/repo2",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = None,
      tags                = None,
      digitalServiceName  = Some("Service A"),
      owningTeams         = List("team4", "team5"),
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teamNames           = List("team2", "team3"),
      prototypeName       = None
    )

  private val repo3 =
    GitRepository(
      name                = "repo3",
      organisation        = Some(Organisation.Mdtp),
      description         = "desc 3",
      url                 = "git/repo3",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Prototype,
      serviceType         = None,
      tags                = None,
      digitalServiceName  = Some("Service A"),
      owningTeams         = Nil,
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teamNames           = List("team1","team2", "team3"),
      prototypeName       = Some("https://repo3.herokuapp.com")
    )

  private val repo4 =
    GitRepository(
      name                = "repo4",
      organisation        = Some(Organisation.Mdtp),
      description         = "desc 4",
      url                 = "git/repo4",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = Some(ServiceType.Frontend),
      tags                = None,
      digitalServiceName  = Some("Service C"),
      owningTeams         = Nil,
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teamNames           = List("team2", "team3"),
      prototypeName       = None
    )

  private val repo5 =
    GitRepository(
      name                = "repo5",
      organisation        = Some(Organisation.Mdtp),
      description         = "desc 5",
      url                 = "git/repo5",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = Some(ServiceType.Backend),
      tags                = None,
      digitalServiceName  = Some("Service C"),
      owningTeams         = Nil,
      language            = None,
      isArchived          = true,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teamNames           = List("team2", "team3"),
      prototypeName       = None
    )

  private val repo6 =
    GitRepository(
      name                = "repo6",
      organisation        = Some(Organisation.Mdtp),
      description         = "desc 6",
      url                 = "git/repo6",
      createdDate         = now,
      lastActiveDate      = now,
      isPrivate           = false,
      repoType            = RepoType.Service,
      serviceType         = Some(ServiceType.Backend),
      tags                = None,
      digitalServiceName  = Some("Service A"),
      owningTeams         = Nil,
      language            = None,
      isArchived          = false,
      defaultBranch       = "main",
      branchProtection    = None,
      isDeprecated        = false,
      teamNames           = List("team2", "team3"),
      prototypeName       = None
    )

  "find" should:
    "find all repos" in:
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find().futureValue
      results should contain (repo1)
      results should contain (repo2)

    "exclude archived repos" in:
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find(isArchived = Some(false)).futureValue
      results should contain (repo1)
      results should not contain (repo2)

    "find repos with team write-access" in:
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find(team = Some("team3")).futureValue
      results should contain only (repo2)

    "find repos owned by team" in:
      repository.collection.insertMany(Seq(repo1, repo2)).toFuture().futureValue
      val results = repository.find(owningTeam = Some("team4")).futureValue
      results should contain only (repo2)

    "find repos containing name" in:
      val foo = repo1.copy(name = "foo")
      val bar = repo1.copy(name = "bar")
      repository.collection.insertMany(Seq(repo1, repo2, foo, bar)).toFuture().futureValue
      val results = repository.find(name = Some("repo")).futureValue
      results should contain theSameElementsAs Seq(repo1, repo2)

    "find repos by repo type" in:
      repository.collection.insertMany(Seq(repo1, repo2, repo3)).toFuture().futureValue
      val results = repository.find(repoType = Some(RepoType.Prototype)).futureValue
      results should contain only (repo3)
      val results2 = repository.find(repoType = Some(RepoType.Service)).futureValue
      results2 should contain theSameElementsAs Seq(repo1, repo2)

    "find repos by digital service name" in:
      repository.collection.insertMany(Seq(repo1, repo2, repo3, repo4, repo5, repo6)).toFuture().futureValue
      val results = repository.find(digitalServiceName = Some("Service A")).futureValue
      results should contain theSameElementsAs Seq(repo2, repo3, repo6)

    "find repos by service type" in:
      repository.collection.insertMany(Seq(repo3, repo4, repo5)).toFuture().futureValue
      val results = repository.find(serviceType = Some(ServiceType.Frontend)).futureValue
      results should contain only repo4
      val results2 = repository.find(serviceType = Some(ServiceType.Backend)).futureValue
      results2 should contain only repo5

    "find repos by organisation" in:
      repository.collection.insertMany(Seq(
        repo1
      , repo2
      , repo3.copy(organisation = None)
      , repo4.copy(organisation = Some(Organisation.External("other")))
      )).toFuture().futureValue

      repository.find(                                                   ).futureValue.size should be (4)
      repository.find(organisation = Some(Organisation.Mdtp)             ).futureValue.size should be (2)
      repository.find(organisation = Some(Organisation.External("other"))).futureValue.size should be (1)

  "putRepos" should:
    "insert new repositories" in:
      repository.putRepos(Seq(repo1,repo2)).futureValue
      findAll().futureValue should contain theSameElementsAs Seq(repo1, repo2)

    "update existing repositories" in:
      insert(repo1.copy(description = "the old description")).futureValue
      repository.putRepos(Seq(repo1,repo2)).futureValue
      findAll().futureValue should contain theSameElementsAs Seq(repo1, repo2)

  "deletedRepos" should:
    "delete repos not in the update list" in:
      insert(repo1).futureValue
      findAll().futureValue should contain (repo1)
      repository.deleteRepo(repo1.name).futureValue
      findAll().futureValue should not contain repo1

  "updateRepoBranchProtection" should:
    "update the branch protection policy of the given repository" in:
      insert(repo1).futureValue

      val bpBefore        = repository.findRepo(repo1.name).map(_.value.branchProtection).futureValue
      val expectedBpAfter = BranchProtection(false, false, false)

      repository.updateRepoBranchProtection(repo1.name, Some(expectedBpAfter)).futureValue

      val bpAfter = repository.findRepo(repo1.name).map(_.value.branchProtection).futureValue
      bpAfter shouldNot be(bpBefore)
      bpAfter shouldBe Some(expectedBpAfter)

  "updateRepo" should:
    "insert a new repository" in:
      repository.putRepo(repo1).futureValue
      repository.putRepo(repo2).futureValue
      findAll().futureValue should contain theSameElementsAs Seq(repo1, repo2)

    "update an existing repository" in:
      repository.putRepo(repo1).futureValue
      val serviceTypeBefore        = repository.findRepo(repo1.name).map(_.value.serviceType).futureValue
      val expectedServiceTypeAfter = ServiceType.Backend
      repository.putRepo(repo1.copy(serviceType = Some(expectedServiceTypeAfter))).futureValue

      val serviceTypeAfter         = repository.findRepo(repo1.name).map(_.value.serviceType).futureValue
      serviceTypeAfter shouldNot be(serviceTypeBefore)
      serviceTypeAfter shouldBe Some(expectedServiceTypeAfter)

  "archiveRepo" should:
    "set the isArchived flag to true" in:
      repository.putRepo(repo1).futureValue

      findAll().futureValue should contain theSameElementsAs Seq(repo1)

      repository.archiveRepo(repo1.name).futureValue

      findAll().futureValue should contain theSameElementsAs Seq(repo1.copy(isArchived = true))

  "deleteRepo" should:
    "delete the repository" in:
      repository.putRepo(repo1).futureValue
      repository.putRepo(repo2).futureValue

      findAll().futureValue should contain theSameElementsAs Seq(repo1, repo2)

      repository.deleteRepo(repo1.name).futureValue

      findAll().futureValue should contain theSameElementsAs Seq(repo2)

  "getDigitalServiceNames" should:
    "retrieve a distinct and ordered list of digital service names" in:
      repository.collection.insertMany(Seq(repo1, repo2, repo3, repo4, repo5, repo6)).toFuture().futureValue

      val digitalServiceNames = Seq("Service A", "Service B")

      repository.getDigitalServiceNames.futureValue should contain theSameElementsInOrderAs digitalServiceNames

    "exclude digital service names where all associated repos are archived" in:
      repository.collection.insertMany(Seq(repo1, repo2, repo3, repo4, repo5, repo6)).toFuture().futureValue

      repository.getDigitalServiceNames.futureValue should not contain "Service C"
