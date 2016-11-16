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

import java.util.Date

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.teamsandrepositories.TeamRepositoryWrapper.TeamRepositoryWrapper
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates

class TeamRepositoryWrapperSpec extends WordSpec with Matchers {

  val timestamp = new Date().getTime


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

  private val createdDateForOther  = 111111123l
  private val lastActiveDateForOther = 111111124l

  "asTeamList" should {

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = Repository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 30)
      val oldDeployableRepo = Repository("repo2", "some desc", "", isInternal = false, repoType = RepoType.Deployable, createdDate = 2, lastActiveDate = 20)
      val newDeployableRepo = Repository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Deployable, createdDate = 3, lastActiveDate = 30)
      val oldOtherRepoWithLatestActiveDate = Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 2, lastActiveDate = 40)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(newDeployableRepo, oldestLibraryRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, oldOtherRepoWithLatestActiveDate))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[Team] = wrapper.asTeamList

      result(0).name shouldBe "teamNameChicken"
      result(0).firstActiveAt shouldBe oldestLibraryRepo.createdDate
      result(0).lastActiveDate shouldBe oldestLibraryRepo.lastActiveDate

      result(1).name shouldBe "teamName"
      result(1).firstActiveAt shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate shouldBe oldOtherRepoWithLatestActiveDate.lastActiveDate


    }

  }

  "asServiceRepoDetailsList" should {

    "include repository with type not Deployable as services if one of the repositories with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "some desc", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "some desc", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        )
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asServiceRepoDetailsList

      result.map(_.name) shouldBe List("repo1", "repo2")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp)

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = Repository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10)
      val oldDeployableRepo = Repository("repo1", "some desc", "", isInternal = false, repoType = RepoType.Deployable, createdDate = 2, lastActiveDate = 20)
      val newDeployableRepo = Repository("repo1", "some desc", "", isInternal = true, repoType = RepoType.Deployable, createdDate = 3, lastActiveDate = 30)
      val newestOtherRepo = Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 4, lastActiveDate = 40)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldestLibraryRepo)),
        TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo)),
        TeamRepositories("teamNameOther", List(newestOtherRepo))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asServiceRepoDetailsList

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(1)
      result.map(_.lastUpdatedAt) shouldBe List(40)

    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = createdDateForOther, lastActiveDate = lastActiveDateForOther)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asServiceRepoDetailsList
      result.size shouldBe 0
    }
  }

  "asLibraryRepoDetailsList" should {
    "not include libraries if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = createdDateForDeployable2, lastActiveDate = lastActiveDateForDeployable2),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asLibraryRepoDetailsList

      result.map(_.name) shouldBe List("repo3", "repo4")

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        )
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asLibraryRepoDetailsList

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(createdDateForLib1)
      result.map(_.lastUpdatedAt) shouldBe List(lastActiveDateForLib2)
    }

    "include as library even if one of the repository with same name is Other" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asLibraryRepoDetailsList

      result.map(_.name) shouldBe List("repo1", "repo3", "repo4")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp, timestamp)
    }

  }

  "findRepositoryDetails" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp))
        )
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Option[RepositoryDetails] = wrapper.findRepositoryDetails("repo1", UrlTemplates(Seq(), Seq(), Map()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Deployable

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = 1, lastActiveDate = 10),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = 2, lastActiveDate = 20),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = 3, lastActiveDate = 30))
        )
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Option[RepositoryDetails] = wrapper.findRepositoryDetails("repo1", UrlTemplates(Seq(), Seq(), Map()))

      val repositoryDetails: RepositoryDetails = result.get
      repositoryDetails.name shouldBe "repo1"
      repositoryDetails.repoType shouldBe RepoType.Deployable
      repositoryDetails.createdAt shouldBe 1
      repositoryDetails.lastActive shouldBe 30

    }

    "find repository as type Library even if one of the repo with same name is not type library" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        ),
        TeamRepositories("teamNameOther1", List(Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Option[RepositoryDetails] = wrapper.findRepositoryDetails("repo1", UrlTemplates(Seq(), Seq(), Map()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Library
      result.get.teamNames shouldBe List("teamName", "teamNameOther", "teamNameOther1")
      result.get.githubUrls.size shouldBe 2

    }


    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.findRepositoryDetails("repo1", UrlTemplates(Seq(), Seq(), Map()))
      result shouldBe None
    }

  }


  "asTeamRepositoryNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamRepositoryNameList("teamName")

      result shouldBe Some(Map(RepoType.Deployable -> List("repo1", "repo2"), RepoType.Library -> List("repo3"), RepoType.Other -> List()))
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamRepositoryNameList("teamName")
      result shouldBe Some(Map(RepoType.Deployable -> List(), RepoType.Library -> List(), RepoType.Other -> List()))
    }

  }

  "asRepositoryTeamNameList" should {

    "group teams by services they own filtering out any duplicates" in {

      val teams = Seq(
        TeamRepositories("team1", List(
          Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          Repository("repo2", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team3", List(
          Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo4", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))))

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asRepositoryTeamNameList()

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))

    }

  }

  "asTeamRepositoryDetailsList" should {

    "get the max last active and min created at for repositories with the same name" in {
      val oldRepo = Repository("repo1", "Some description", "", isInternal = false, repoType = RepoType.Deployable, createdDate = 1, lastActiveDate = 10)
      val newRepo = Repository("repo1", "Some description", "", isInternal = true, repoType = RepoType.Deployable, createdDate = 2, lastActiveDate = 20)

      val teams = Seq(
        TeamRepositories("teamName", List(oldRepo, newRepo)),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamRepositoryDetailsList("teamName")

      result shouldBe Some(Map(RepoType.Deployable -> List(RepositoryDisplayDetails("repo1", 1, 20))))
    }

  }

  }
