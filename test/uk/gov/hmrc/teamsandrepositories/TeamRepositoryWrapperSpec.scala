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

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.teamsandrepositories.TeamRepositoryWrapper.TeamRepositoryWrapper
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates

class TeamRepositoryWrapperSpec extends WordSpec with Matchers {

  val timestamp = LocalDateTime.now()

  "asServiceNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        )
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[String] = wrapper.asServiceNameList

      result shouldBe List("repo1", "repo2")

    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "", isInternal = false, repoType = RepoType.Deployable,createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[String] = wrapper.asServiceNameList
      result.size shouldBe 0
    }
  }

  "asLibraryNameList" should {
    "not include libraries if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo4", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[String] = wrapper.asLibraryNameList

      result shouldBe List("repo3", "repo4")

    }

    "include as library even if one of the repository with same name is Other" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo4", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[String] = wrapper.asLibraryNameList

      result shouldBe List("repo1", "repo3", "repo4")
    }

  }

  "findRepositoryDetails" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library,createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other,createdDate = timestamp, lastActiveDate = timestamp))
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
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other,createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable,createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library,createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))
        ),
        TeamRepositories("teamNameOther1", List(Repository("repo1", "", isInternal = false, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
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
          Repository("repo1-prototype", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
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
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamRepositoryNameList("teamName")

      result shouldBe Some(Map(RepoType.Deployable -> List("repo1", "repo2"), RepoType.Library -> List("repo3"), RepoType.Other -> List()))
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Other, createdDate = timestamp, lastActiveDate = timestamp)))
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
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          Repository("repo2", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team2", List(
          Repository("repo2", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))),
        TeamRepositories("team3", List(
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
          Repository("repo4", "", isInternal = true, repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp))))

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asRepositoryTeamNameList()

      result should contain ("repo1" -> Seq("team1"))
      result should contain ("repo2" -> Seq("team1", "team2"))
      result should contain ("repo3" -> Seq("team2", "team3"))
      result should contain ("repo4" -> Seq("team3"))

    }

  }

}
