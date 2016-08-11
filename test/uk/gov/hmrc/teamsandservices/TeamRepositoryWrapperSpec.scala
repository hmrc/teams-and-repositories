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

package uk.gov.hmrc.teamsandservices

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.teamsandservices.TeamRepositoryWrapper.TeamRepositoryWrapper
import uk.gov.hmrc.teamsandservices.config.UrlTemplates

class TeamRepositoryWrapperSpec extends WordSpec with Matchers {

  "asServiceNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Library)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[String] = wrapper.asServiceNameList

      result shouldBe List("repo1", "repo2")

    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "", isInternal = false, repoType = RepoType.Deployable)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Other)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result: Seq[String] = wrapper.asServiceNameList
      result.size shouldBe 0
    }
  }

  "findService" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Library)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.findService("repo1", UrlTemplates(Seq(), Seq(), Map()))

      result.get.name shouldBe "repo1"
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "", isInternal = false, repoType = RepoType.Deployable)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Other)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.findService("repo1", UrlTemplates(Seq(), Seq(), Map()))
      result shouldBe None
    }

  }


  "asTeamServiceNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1", "", isInternal = false, repoType = RepoType.Deployable),
          Repository("repo2", "", isInternal = true, repoType = RepoType.Deployable),
          Repository("repo1", "", isInternal = true, repoType = RepoType.Other),
          Repository("repo3", "", isInternal = true, repoType = RepoType.Library)
        )
        ),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Library)))
      )
      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamServiceNameList("teamName")

      result shouldBe Some(List("repo1", "repo2"))
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
          Repository("repo1-prototype", "", isInternal = false, repoType = RepoType.Deployable)
        )),
        TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, repoType = RepoType.Other)))
      )

      val wrapper: TeamRepositoryWrapper = new TeamRepositoryWrapper(teams)
      val result = wrapper.asTeamServiceNameList("teamName")
      result shouldBe Some(List())
    }

  }



}
