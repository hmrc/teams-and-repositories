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

import org.scalatest.{Matchers, WordSpec, FunSuite}

class TeamRepositoriesSpec extends WordSpec with Matchers {

  "TeamRepositories" should {
    "get repositories by type" in {

      val teamRepos = TeamRepositories("A", List(
        Repository("r1", "", repoType = RepoType.Deployable),
        Repository("r2", "", repoType = RepoType.Library),
        Repository("r3", "", repoType = RepoType.Deployable),
        Repository("r4", "")
      ))

      teamRepos.repositoriesByType(RepoType.Deployable) shouldBe List(
        Repository("r1", "", repoType = RepoType.Deployable),
        Repository("r3", "", repoType = RepoType.Deployable)
      )


    }
  }


}
