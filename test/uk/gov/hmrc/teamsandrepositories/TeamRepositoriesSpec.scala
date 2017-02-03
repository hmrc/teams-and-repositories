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

import org.scalatest.{FunSuite, Matchers, WordSpec}

class TeamRepositoriesSpec extends WordSpec with Matchers {

  val timestamp = new Date().getTime


  "TeamRepositories" should {
    "get repositories by type" in {

      val teamRepos = TeamRepositories("A", List(
        GitRepository("r1", "some desc", "url", repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
        GitRepository("r2", "some desc", "url", repoType = RepoType.Library, createdDate = timestamp, lastActiveDate = timestamp),
        GitRepository("r3", "some desc", "url", repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
        GitRepository("r4", "some desc", "url", createdDate = timestamp, lastActiveDate = timestamp)
      ))

      teamRepos.repositoriesByType(RepoType.Service) shouldBe List(
        GitRepository("r1", "some desc", "url", repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp),
        GitRepository("r3", "some desc", "url", repoType = RepoType.Service, createdDate = timestamp, lastActiveDate = timestamp)
      )


    }
  }


}
