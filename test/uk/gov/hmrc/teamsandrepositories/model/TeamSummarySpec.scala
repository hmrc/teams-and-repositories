/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.time.temporal.ChronoUnit

class TeamSummarySpec extends AnyWordSpec with Matchers:

  "TeamSummary.apply" in new Setup:
    val gitRepo1: GitRepository =
      gitRepository.copy(name = "repo-one",   owningTeams = Seq("A"),      teamNames = List("A", "B", "C"))

    val gitRepo2: GitRepository =
      gitRepository.copy(name = "repo-two",   owningTeams = Seq("B"),      teamNames = List("A", "B", "C"), lastActiveDate = now.minus(5, ChronoUnit.DAYS))

    val gitRepo3: GitRepository =
      gitRepository.copy(name = "repo-three", owningTeams = Seq("A", "B"), teamNames = List("A", "B", "C"), lastActiveDate = now)

    TeamSummary.apply("A", List(gitRepo1, gitRepo3), now) shouldBe TeamSummary("A", Some(now), Seq("repo-one", "repo-three"), now)
    TeamSummary.apply("B", List(gitRepo2, gitRepo3), now) shouldBe TeamSummary("B", Some(now), Seq("repo-two", "repo-three"), now)

  trait Setup:
    val now: Instant = Instant.now()

    val gitRepository: GitRepository =
      GitRepository(
        name          = "",
        organisation  = Some(Organisation.Mdtp),
        description   = "some description",
        url           = "url",
        createdDate   = now,
        lastActiveDate= now,
        repoType      = RepoType.Other,
        owningTeams   = Seq.empty,
        teamNames     = List.empty,
        language      = Some("Scala"),
        isArchived    = false,
        defaultBranch = "main"
    )
