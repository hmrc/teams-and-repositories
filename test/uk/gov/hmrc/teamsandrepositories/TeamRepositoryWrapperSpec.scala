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


  private val createdDateForDeployable1 = 11111111l
  private val createdDateForDeployable2 = 111111112l
  private val lastActiveDateForDeployable1 = 111111114l
  private val lastActiveDateForDeployable2 = 111111115l

  private val createdDateForLib1 = 111111117l
  private val createdDateForLib2 = 111111118l
  private val createdDateForLib3 = 111111119l
  private val lastActiveDateForLib1 = 111111120l
  private val lastActiveDateForLib2 = 111111121l
  private val lastActiveDateForLib3 = 111111122l

  private val createdDateForOther  = 111111123l
  private val lastActiveDateForOther = 111111124l

  "asServiceRepoDetailsList" should {

    "include repository with type not Deployable as services if one of the repositories with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
            Repository("repo1", "some desc",  "", isInternal = false, repoType = RepoType.Deployable, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1),
          Repository("repo2", "some desc",  "", isInternal = true, repoType = RepoType.Deployable, createdDate = createdDateForDeployable2, lastActiveDate = lastActiveDateForDeployable2),
          Repository("repo1", "some desc",  "", isInternal = true, repoType = RepoType.Other, createdDate = createdDateForOther, lastActiveDate = lastActiveDateForOther),
          Repository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "some desc", "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib3, lastActiveDate = lastActiveDateForLib3))
        )
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asServiceRepoDetailsList

      result.map(_.name) shouldBe List("repo1", "repo2")
      result.map(_.createdAt) shouldBe List(createdDateForDeployable1, createdDateForDeployable2)
      result.map(_.lastUpdatedAt) shouldBe List(lastActiveDateForDeployable1, lastActiveDateForDeployable2)

    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "Some description", "", isInternal = false, repoType = RepoType.Deployable,createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Other, createdDate = createdDateForOther, lastActiveDate = lastActiveDateForOther)))
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
          Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Deployable, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1),
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Deployable, createdDate = createdDateForDeployable2, lastActiveDate = lastActiveDateForDeployable2),
          Repository("repo1", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo4", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asLibraryRepoDetailsList

      result.map(_.name) shouldBe List("repo3", "repo4")

    }

    "include as library even if one of the repository with same name is Other" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Other, createdDate = createdDateForOther, lastActiveDate = lastActiveDateForOther),
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Deployable, createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1),
          Repository("repo1", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForDeployable1),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo4", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = createdDateForLib3, lastActiveDate = lastActiveDateForLib3)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[RepositoryDisplayDetails] = wrapper.asLibraryRepoDetailsList

      result.map(_.name) shouldBe List("repo1", "repo3", "repo4")
      result.map(_.createdAt) shouldBe List(createdDateForOther,createdDateForLib2,createdDateForLib3)
      result.map(_.lastUpdatedAt) shouldBe List(lastActiveDateForOther, lastActiveDateForLib2, lastActiveDateForLib3)
    }

  }

  "findRepositoryDetails" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description" , "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library,createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description" , "", isInternal = true, repoType = RepoType.Other,createdDate = timestamp, lastActiveDate = timestamp))
        )
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Option[RepositoryDetails] = wrapper.findRepositoryDetails("repo1", UrlTemplates(Seq(), Seq(), Map()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe RepoType.Deployable

    }

    "find repository as type Library even if one of the repo with same name is not type library" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "Some description" , "", isInternal = true, repoType = RepoType.Other,createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Deployable,createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library,createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        ),
        TeamRepositories("teamNameOther1", List(Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
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
          Repository("repo1-prototype", "Some description" , "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
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
          Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "Some description" , "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamRepositoryNameList("teamName")

      result shouldBe Some(Map(RepoType.Deployable -> List("repo1", "repo2"), RepoType.Library -> List("repo3"), RepoType.Other -> List()))
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "Some description" , "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
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
          Repository("repo1", "Some description" , "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          Repository("repo2", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team3", List(
          Repository("repo3", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo4", "Some description" , "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))))

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asRepositoryTeamNameList()

      result should contain ("repo1" -> Seq("team1"))
      result should contain ("repo2" -> Seq("team1", "team2"))
      result should contain ("repo3" -> Seq("team2", "team3"))
      result should contain ("repo4" -> Seq("team3"))

    }

  }

}
