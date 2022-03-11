/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.Instant
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.models.RepoType.{Library, Other, Prototype, Service}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.controller.model.Team
import uk.gov.hmrc.teamsandrepositories.models.{DigitalServiceRepository, GitRepository, TeamRepositories}

import scala.collection.immutable.ListMap

class TeamRepositoriesSpec extends AnyWordSpec with Matchers with OptionValues {
  import TeamRepositories._
  val now = Instant.now()

  private val description = "Some description"

  "getAllRepositories" should {
    "deduplicate results" in {
      val teams = Seq(
        TeamRepositories(
          teamName     = "team1",
          repositories = List(
                          GitRepository(
                            name               = "repo1",
                            description        = "some desc",
                            url                = "",
                            createdDate        = now,
                            lastActiveDate     = now,
                            repoType           = Library,
                            digitalServiceName = None,
                            language           = Some("Scala"),
                            isArchived         = false,
                            defaultBranch      = "main"
                          )
                        ),
          createdDate = Some(now),
          updateDate   = now
        ),
        TeamRepositories(
          teamName     = "team2",
          repositories = List(
                          GitRepository(
                            name               = "repo1",
                            description        = "some desc",
                            url                = "",
                            createdDate        = now,
                            lastActiveDate     = now,
                            repoType           = Library,
                            digitalServiceName = None,
                            language           = Some("Scala"),
                            isArchived         = false,
                            defaultBranch      = "main"
                          )
                        ),
          createdDate  = Some(now),
          updateDate   = now
        )
      )

      val res = TeamRepositories.getAllRepositories(teams)

      res.length shouldBe 1
    }

    "deduplicate results when there is a last modified mismatch" in {
      val teams = Seq(
        TeamRepositories(
          teamName     = "team1",
          repositories = List(
                           GitRepository(
                             name               = "repo1",
                             description        = "some desc",
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Library,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        ),
        TeamRepositories(
          teamName     = "team2",
          repositories = List(
                           GitRepository(
                             name               = "repo1",
                             description        = "some desc",
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now.minusSeconds(1000),
                             repoType           = Library,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        )
      )

      val res = TeamRepositories.getAllRepositories(teams)

      res.length shouldBe 1
    }
  }

  "findRepositoryDetails" should {
    "find a repository" in { // todo(konrad) add more initial TeamRepositories as test has little value
      val teams = Seq(
        TeamRepositories(
          teamName     = "teamName",
          repositories = List(
                           GitRepository(
                             name               = "repo1",
                             description        = description,
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Library,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        )
      )

      TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(ListMap())) shouldBe defined
    }

    "find a repository where the name has a different case" in {
      val teams = Seq(
        TeamRepositories(
          teamName     = "teamName",
          repositories = List(
                           GitRepository(
                             name               = "repo1",
                             description        = description,
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Library,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        )
      )

      val Some(repositoryDetails) =
        TeamRepositories.findRepositoryDetails(teams, "REPO1", UrlTemplates(ListMap()))

      repositoryDetails.name       shouldBe "repo1"
      repositoryDetails.repoType   shouldBe Library
      repositoryDetails.createdAt  shouldBe now
      repositoryDetails.lastActive shouldBe now
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories(
          teamName     = "teamName",
          repositories = List(
                           GitRepository(
                             name               = "repo1-prototype",
                             description        = description,
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Service,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        ),
        TeamRepositories(
          teamName     = "teamNameOther",
          repositories = List(
                           GitRepository(
                             name               = "repo3",
                             description        = description,
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Other,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        )
      )

      val result = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(ListMap()))
      result shouldBe None
    }
  }

  "getRepositoryToTeamNames" should {
    "group teams by services they own filtering out any duplicates" in {
      val teams = Seq(
        TeamRepositories(
          teamName     = "team1",
          repositories = List(
                           GitRepository(
                             name               = "repo1",
                             description        = description,
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Service,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           ),
                           GitRepository(
                             name               = "repo2",
                             description        = description,
                             url                = "",
                             createdDate        = now,
                             lastActiveDate     = now,
                             repoType           = Library,
                             digitalServiceName = None,
                             language           = Some("Scala"),
                             isArchived         = false,
                             defaultBranch      = "main"
                           )
                         ),
          createdDate = Some(now),
          updateDate   = now
        ),
        TeamRepositories(
          teamName     = "team2",
          repositories = List(
            GitRepository(
              name               = "repo2",
              description        = description,
              url                = "",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            ),
            GitRepository(
              name               = "repo3",
              description        = description,
              url                = "",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate = Some(now),
          updateDate   = now
        ),
        TeamRepositories(
          teamName     = "team2",
          repositories = List(
            GitRepository(
              name               = "repo2",
              description        = description,
              url                = "",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            ),
            GitRepository(
              name               = "repo3",
              description        = description,
              url                = "",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate = Some(now),
          updateDate   = now
        ),
        TeamRepositories(
          teamName     = "team3",
          repositories = List(
            GitRepository(
              name               = "repo3",
              description        = description,
              url                = "",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            ),
            GitRepository(
              name               = "repo4",
              description        = description,
              url                = "",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate = Some(now),
          updateDate   = now
        )
      )

      val result = TeamRepositories.getRepositoryToTeamNames(teams)

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))
    }
  }

  "findDigitalServiceDetails" should {
    "get the correct Digital Service Info" in {
      val digitalServiceName = "DigitalService1"

      val repo1 =
        GitRepository(
          name               = "repo1",
          description        = "n/a",
          url                = "n/a",
          createdDate        = now,
          lastActiveDate     = now,
          repoType           = Library,
          digitalServiceName = Some(digitalServiceName),
          language           = Some("Scala"),
          isArchived         = false,
          defaultBranch      = "main"
        )

      val mostRecentTimestamp = repo1.lastActiveDate.plusSeconds(1)

      val repo2 = repo1.copy(
        name           = "repo2",
        repoType       = Service,
        lastActiveDate = mostRecentTimestamp
      )

      val repo3 =
        repo1.copy(
          name               = "repo3",
          digitalServiceName = Some("Unexpected Service Name")
        )

      val repo4 =
        repo1.copy(
          name               = "repo4",
          digitalServiceName = None
        )

      val teamsAndRepositories =
        List(
          TeamRepositories("team1", List(repo1, repo2), Some(now), now),
          TeamRepositories("team2", List(repo3), Some(now), now),
          TeamRepositories("team3", List(repo1, repo2, repo3), Some(now), now),
          TeamRepositories("team4", List(repo2, repo4), Some(now), now)
        )

      val result = findDigitalServiceDetails(teamsAndRepositories, "DigitalService1")

      result.value.name shouldBe digitalServiceName
      result.value.repositories shouldBe Seq(
        DigitalServiceRepository(
          name          = repo1.name,
          createdAt     = repo1.createdDate,
          lastUpdatedAt = repo1.lastActiveDate,
          repoType      = repo1.repoType,
          teamNames     = Seq("team1", "team3"),
          archived      = false
        ),
        DigitalServiceRepository(
          name          = repo2.name,
          createdAt     = repo2.createdDate,
          lastUpdatedAt = repo2.lastActiveDate,
          repoType      = repo2.repoType,
          teamNames     = Seq("team1", "team3", "team4"),
          archived      = false
        )
      )
      result.value.lastUpdatedAt shouldBe mostRecentTimestamp
    }

    "find the Digital Service when the name is of a different case" in {
      val teams = Seq(
        TeamRepositories(
          teamName     = "teamName",
          repositories = List(
            GitRepository(
              name               = "repo1",
              description        = description,
              url                = "n/a",
              createdDate        = now,
              lastActiveDate     = now,
              digitalServiceName = Some("DigitalService1"),
              language           = None,
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(now),
          updateDate   = now
        ))

      findDigitalServiceDetails(teams, "digitalservice1").value.name shouldBe "DigitalService1"
    }
  }

  "toTeam" should {
    val teamCreatedDate = Instant.parse("2019-04-01T12:00:00Z")
    val oldDeployableRepo = GitRepository(
      name               = "repo1",
      description        = description,
      url                = "",
      createdDate        = Instant.ofEpochMilli(1),
      lastActiveDate     = Instant.ofEpochMilli(10),
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main"
    )

    val newDeployableRepo = GitRepository(
      name               = "repo1",
      description        = description,
      url                = "",
      createdDate        = Instant.ofEpochMilli(2),
      lastActiveDate     = Instant.ofEpochMilli(20),
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main"
    )

    val newLibraryRepo = GitRepository(
      name               = "repo1",
      description        = description,
      url                = "",
      createdDate        = Instant.ofEpochMilli(3),
      lastActiveDate     = Instant.ofEpochMilli(30),
      repoType           = Library,
      digitalServiceName = None,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main"
    )

    val newOtherRepo = GitRepository(
      name               = "repo1",
      description        = description,
      url                = "",
      createdDate        = Instant.ofEpochMilli(4),
      lastActiveDate     = Instant.ofEpochMilli(40),
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main"
    )

    "get the max last active for repositories" in {
      val teamRepository = TeamRepositories(
        teamName     = "teamName",
        repositories = List(oldDeployableRepo, newDeployableRepo),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )

      val result = teamRepository.toTeam(sharedRepos = Nil, includeRepos = true)

      result shouldBe Team(
         name             = "teamName",
         createdDate      = Some(teamCreatedDate),
         lastActiveDate   = Some(Instant.ofEpochMilli(20)),
         repos            = Some(Map(
                              Service   -> List("repo1"),
                              Library   -> List(),
                              Prototype -> List(),
                              Other     -> List()
                            ))
       )
    }

    "include all repository types when get the max last active for team" in {
      val teamRepository = TeamRepositories(
        teamName     = "teamName",
        repositories = List(oldDeployableRepo, newLibraryRepo, newOtherRepo),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )

      val result = teamRepository.toTeam(sharedRepos = Nil, includeRepos = true)

      result shouldBe Team(
        name            = "teamName",
        createdDate     = Some(teamCreatedDate),
        lastActiveDate  = Some(Instant.ofEpochMilli(40)),
        repos           = Some(Map(
                            Service   -> List("repo1"),
                            Library   -> List("repo1"),
                            Prototype -> List(),
                            Other     -> List("repo1")
                          ))
      )
    }

    "get all teams and their repositories grouped by repo type" in {
      val repo1 = GitRepository(
        name               = "repo1",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(1),
        lastActiveDate     = Instant.ofEpochMilli(10),
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        owningTeams        = List("teamName"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo2 = GitRepository(
        name               = "repo2",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(1),
        lastActiveDate     = Instant.ofEpochMilli(10),
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo3 = GitRepository(
        name               = "repo3",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(20),
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo4 = GitRepository(
        name               = "repo4",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(2),
        lastActiveDate     = Instant.ofEpochMilli(20),
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo5 = GitRepository(
        name               = "repo5",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(3),
        lastActiveDate     = Instant.ofEpochMilli(30),
        repoType           = Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo6 = GitRepository(
        name               = "repo6",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(3),
        lastActiveDate     = Instant.ofEpochMilli(30),
        repoType           = Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo7 = GitRepository(
        name               = "repo7",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(4),
        lastActiveDate     = Instant.ofEpochMilli(40),
        repoType           = Prototype,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val repo8 = GitRepository(
        name               = "repo8",
        description        = description,
        url                = "",
        createdDate        = Instant.ofEpochMilli(4),
        lastActiveDate     = Instant.ofEpochMilli(40),
        repoType           = Prototype,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      val teamRepository =
        TeamRepositories(
          teamName     = "teamName",
          repositories = List(repo1, repo2, repo3, repo4, repo5),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )

      teamRepository.toTeam(sharedRepos = Nil, includeRepos = true) shouldEqual Team(
          name            = "teamName",
          createdDate     = Some(teamCreatedDate),
          lastActiveDate  = Some(Instant.ofEpochMilli(30)),
          repos           = Some(Map(
                              Service   -> List("repo1", "repo2"),
                              Library   -> List("repo3", "repo4"),
                              Prototype -> List(),
                              Other     -> List("repo5")
                            )),
          ownedRepos      = List("repo1")
        )

      val teamOtherRepository =
        TeamRepositories(
          teamName     = "teamNameOther",
          repositories = List(repo4, repo5, repo6, repo7, repo8),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )

      teamOtherRepository.toTeam(sharedRepos = Nil, includeRepos = true) shouldEqual Team(
        name            = "teamNameOther",
        createdDate     = Some(teamCreatedDate),
        lastActiveDate  = Some(Instant.ofEpochMilli(40)),
        repos           = Some(Map(
                            Service   -> List(),
                            Library   -> List("repo4"),
                            Prototype -> List("repo7", "repo8"),
                            Other     -> List("repo5", "repo6")
                          ))
      )
    }
  }
}
