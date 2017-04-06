/*
 * Copyright 2016 HM Revenue & Customs
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

import java.time.LocalDateTime
import java.util.Date

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates

import scala.collection.immutable.ListMap

class TeamRepositoriesSpec extends WordSpec with Matchers {

  val timestamp = new Date().getTime
  val now = LocalDateTime.now()


  private val createdDateForDeployable1 = 1
  private val createdDateForDeployable2 = 2
  private val createdDateForLib1 = 3
  private val createdDateForLib2 = 4
  private val createdDateForLib3 = 5

  private val lastActiveDateForDeployable1 = 10
  private val lastActiveDateForDeployable2 = 20
  private val lastActiveDateForLib1 = 30
  private val lastActiveDateForLib2 = 40
  private val lastActiveDateForLib3 = 50

  private val createdDateForOther = 111111123l
  private val lastActiveDateForOther = 111111124l

  "getTeamList" should {

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10)
      val oldDeployableRepo = GitRepository("repo2", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20)
      val newDeployableRepo = GitRepository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 3, lastActiveDate = 30)
      val oldOtherRepoWithLatestActiveDate = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 2, lastActiveDate = 40)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(newDeployableRepo, oldestLibraryRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, oldOtherRepoWithLatestActiveDate)),
        TeamRepositories("teamNameNotActive", List())
      )

      val result: Seq[Team] = TeamRepositories.getTeamList(teams, Nil)

      result(0).name shouldBe "teamNameChicken"
      result(0).firstActiveDate.get shouldBe oldestLibraryRepo.createdDate
      result(0).lastActiveDate.get shouldBe newDeployableRepo.lastActiveDate

      result(1).name shouldBe "teamName"
      result(1).firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate.get shouldBe oldOtherRepoWithLatestActiveDate.lastActiveDate

      result(2).name shouldBe "teamNameNotActive"
      result(2).firstActiveDate shouldBe None
      result(2).lastActiveDate shouldBe None

    }

    "Exclude specified repos in calculating activity max and min dates" in {

      val oldLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 2, lastActiveDate = 20)
      val oldDeployableRepo = GitRepository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 3, lastActiveDate = 30)
      val newLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 4, lastActiveDate = 40)
      val newDeployableRepo = GitRepository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 5, lastActiveDate = 50)
      val newIgnoreRepo = GitRepository("ignoreRepo", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = 1, lastActiveDate = 10000)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldLibraryRepo, newDeployableRepo, newIgnoreRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newIgnoreRepo)),
        TeamRepositories("teamNameNotActive", List())
      )

      val result: Seq[Team] = TeamRepositories.getTeamList(teams, List("ignoreRepo"))

      result(0).name shouldBe "teamNameChicken"
      result(0).firstActiveDate.get shouldBe oldLibraryRepo.createdDate
      result(0).lastActiveDate.get shouldBe newDeployableRepo.lastActiveDate

      result(1).name shouldBe "teamName"
      result(1).firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate.get shouldBe newLibraryRepo.lastActiveDate

      result(2).name shouldBe "teamNameNotActive"
      result(2).firstActiveDate shouldBe None
      result(2).lastActiveDate shouldBe None

    }

  }

  "getServiceRepoDetailsList" should {

    "include repository with type not Deployable as services if one of the repositories with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "some desc", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        )
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Service)

      result.map(_.name) shouldBe List("repo1", "repo2")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp)
    }


    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10)
      val oldDeployableRepo = GitRepository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20)
      val newDeployableRepo = GitRepository("repo1", "some desc", "", isInternal = true, repoType = RepoType.Service, createdDate = 3, lastActiveDate = 30)
      val newestOtherRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 4, lastActiveDate = 40)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldestLibraryRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo)),
        TeamRepositories("teamNameOther", List(newestOtherRepo))
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Service)

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(1)
      result.map(_.lastUpdatedAt) shouldBe List(40)

    }
  }

  "getLibraryRepoDetailsList" should {
    "not include libraries if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = createdDateForDeployable2, lastActiveDate = lastActiveDateForDeployable2),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        ),
        TeamRepositories("teamNameOther", List(GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Library)

      result.map(_.name) shouldBe List("repo3", "repo4")

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        )
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Library)

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(createdDateForLib1)
      result.map(_.lastUpdatedAt) shouldBe List(lastActiveDateForLib2)
    }

    "include as library even if one of the repository with same name is Other" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == RepoType.Library)

      result.map(_.name) shouldBe List("repo1", "repo3", "repo4")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp, timestamp)
    }

  }

  "findRepositoryDetails" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp))
        )
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Service

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 3, lastActiveDate = 30))
        )
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      val repositoryDetails: RepositoryDetails = result.get
      repositoryDetails.name shouldBe "repo1"
      repositoryDetails.repoType shouldBe RepoType.Service
      repositoryDetails.createdAt shouldBe 1
      repositoryDetails.lastActive shouldBe 30

    }

    "find repository as type Library even if one of the repo with same name is not type library" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        ),
        TeamRepositories("teamNameOther1", List(GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Library
      result.get.teamNames shouldBe List("teamName", "teamNameOther", "teamNameOther1")
      result.get.githubUrls.size shouldBe 2

    }


    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1-prototype", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val result = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))
      result shouldBe None
    }

  }


  "getTeamRepositoryNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val result = TeamRepositories.getTeamRepositoryNameList(teams, "teamName")

      result shouldBe Some(Map(RepoType.Service -> List("repo1", "repo2"), RepoType.Library -> List("repo3"), RepoType.Prototype -> List(), RepoType.Other -> List()))
    }


  }

  "asRepositoryTeamNameList" should {

    "group teams by services they own filtering out any duplicates" in {

      val teams = Seq(
        TeamRepositories("team1", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team3", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))))

      val result = TeamRepositories.getRepositoryToTeamNameList(teams)

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))

    }

  }


  "findTeam" should {

    val oldDeployableRepo = GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = 1, lastActiveDate = 10)
    val newDeployableRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Service, createdDate = 2, lastActiveDate = 20)
    val newLibraryRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = 3, lastActiveDate = 30)
    val newOtherRepo = GitRepository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 4, lastActiveDate = 40)
    val sharedRepo = GitRepository("sharedRepo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 5, lastActiveDate = 50)

    val teams = Seq(
      TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo)),
      TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
    )


    "get the max last active and min created at for repositories with the same name" in {
      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result shouldBe Some(Team(name = "teamName", firstActiveDate = Some(1), lastActiveDate = Some(20), firstServiceCreationDate = Some(oldDeployableRepo.createdDate),
        repos = Some(Map(
          RepoType.Service -> List("repo1"),
          RepoType.Library -> List(),
          RepoType.Prototype -> List(),
          RepoType.Other -> List())))
      )
    }

    "Include all repository types when get the max last active and min created at for team" in {

      val teams = Seq(
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo)),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result shouldBe Some(
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Some(Map(
            RepoType.Service -> List("repo1"),
            RepoType.Library -> List("repo1"),
            RepoType.Prototype -> List(),
            RepoType.Other -> List("repo1")
          ))
        )
      )
    }

    "Exclude all shared repositories when calculating the min and max activity dates for a team" in {

      val teams = Seq(
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo)),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result shouldBe Some(
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Some(Map(
            RepoType.Service -> List("repo1"),
            RepoType.Library -> List("repo1"),
            RepoType.Prototype -> List(),
            RepoType.Other -> List("repo1", "sharedRepo1")
          ))
        )
      )
    }


    "populate firstServiceCreation date by looking at only the service repository" in {

      val teams = Seq(
        TeamRepositories("teamName", List(newDeployableRepo, oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo)),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result shouldBe Some(
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Some(Map(
            RepoType.Service -> List("repo1"),
            RepoType.Library -> List("repo1"),
            RepoType.Prototype -> List(),
            RepoType.Other -> List("repo1", "sharedRepo1")
          ))
        )
      )
    }


    "return None when queried with a non existing team" in {
      TeamRepositories.findTeam(teams, "nonExistingTeam", Nil) shouldBe None
    }

  }


  "getAllRepositories" should {
    "discard duplicate repositories according to the repository type configured hierarchy" in {

      val teams = Seq(
        TeamRepositories("team1", List(
          GitRepository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          GitRepository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team3", List(
          GitRepository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          GitRepository("repo5-prototype", "Some description", "", isInternal = true, repoType = RepoType.Prototype, createdDate = timestamp, lastActiveDate = timestamp))))

      TeamRepositories.getAllRepositories(teams) shouldBe Seq(
        Repository(name = "repo1", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Service),
        Repository(name = "repo2", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Library),
        Repository(name = "repo3", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Library),
        Repository(name = "repo4", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Other),
        Repository(name = "repo5-prototype", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = RepoType.Prototype)
      )

    }

  }
}
