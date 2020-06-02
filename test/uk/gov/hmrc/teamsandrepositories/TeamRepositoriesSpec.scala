/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.RepoType.{Library, Other, Prototype, Service}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.controller.model.{Repository, Team}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories.{DigitalServiceRepository, findDigitalServiceDetails}

import scala.collection.immutable.ListMap

class TeamRepositoriesSpec extends AnyWordSpec with Matchers with OptionValues {

  val timestamp   = new Date().getTime
  val now         = LocalDateTime.now()
  val nowInMillis = now.toInstant(ZoneOffset.UTC).toEpochMilli

  private val description = "Some description"
  "getTeamList" should {

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository(
        "repo1",
        "some desc",
        "",
        createdDate    = 1,
        lastActiveDate = 10,
        // isInternal         = false,
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val oldDeployableRepo = GitRepository(
        "repo2",
        "some desc",
        "",
        createdDate    = 2,
        lastActiveDate = 20,
        // isInternal         = false,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val newDeployableRepo = GitRepository(
        "repo3",
        "some desc",
        "",
        createdDate    = 3,
        lastActiveDate = 30,
        // isInternal         = true,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val oldOtherRepoWithLatestActiveDate = GitRepository(
        "repo1",
        description,
        "",
        createdDate    = 2,
        lastActiveDate = 40,
        // isInternal         = true,
        repoType           = Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(newDeployableRepo, oldestLibraryRepo), System.currentTimeMillis()),
        TeamRepositories(
          "teamName",
          List(oldDeployableRepo, oldOtherRepoWithLatestActiveDate),
          System.currentTimeMillis()),
        TeamRepositories("teamNameNotActive", List(), System.currentTimeMillis())
      )

      val result: Seq[Team] = TeamRepositories.getTeamList(teams, Nil)

      result(0).name                shouldBe "teamNameChicken"
      result(0).firstActiveDate.get shouldBe oldestLibraryRepo.createdDate
      result(0).lastActiveDate.get  shouldBe newDeployableRepo.lastActiveDate

      result(1).name                shouldBe "teamName"
      result(1).firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate.get  shouldBe oldOtherRepoWithLatestActiveDate.lastActiveDate

      result(2).name            shouldBe "teamNameNotActive"
      result(2).firstActiveDate shouldBe None
      result(2).lastActiveDate  shouldBe None

    }

    "Exclude specified repos in calculating activity max and min dates" in {

      val oldLibraryRepo = GitRepository(
        "repo1",
        "some desc",
        "",
        createdDate    = 2,
        lastActiveDate = 20,
        // isInternal         = false,
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val oldDeployableRepo = GitRepository(
        "repo2",
        "some desc",
        "",
        createdDate    = 3,
        lastActiveDate = 30,
        // isInternal         = true,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val newLibraryRepo = GitRepository(
        "repo1",
        "some desc",
        "",
        createdDate    = 4,
        lastActiveDate = 40,
        // isInternal         = false,
        repoType           = Library,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val newDeployableRepo = GitRepository(
        "repo2",
        "some desc",
        "",
        createdDate    = 5,
        lastActiveDate = 50,
        // isInternal         = true,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false)
      val newIgnoreRepo = GitRepository(
        "ignoreRepo",
        "some desc",
        "",
        createdDate    = 1,
        lastActiveDate = 10000,
        // isInternal         = false,
        repoType           = Service,
        digitalServiceName = None,
        language           = Some("Scala"),
        archived           = false
      )

      val teams = Seq(
        TeamRepositories(
          "teamNameChicken",
          List(oldLibraryRepo, newDeployableRepo, newIgnoreRepo),
          System.currentTimeMillis()),
        TeamRepositories(
          "teamName",
          List(oldDeployableRepo, newLibraryRepo, newIgnoreRepo),
          System.currentTimeMillis()),
        TeamRepositories("teamNameNotActive", List(), System.currentTimeMillis())
      )

      val result: Seq[Team] = TeamRepositories.getTeamList(teams, List("ignoreRepo"))

      result(0).name                shouldBe "teamNameChicken"
      result(0).firstActiveDate.get shouldBe oldLibraryRepo.createdDate
      result(0).lastActiveDate.get  shouldBe newDeployableRepo.lastActiveDate

      result(1).name                shouldBe "teamName"
      result(1).firstActiveDate.get shouldBe oldDeployableRepo.createdDate
      result(1).lastActiveDate.get  shouldBe newLibraryRepo.lastActiveDate

      result(2).name            shouldBe "teamNameNotActive"
      result(2).firstActiveDate shouldBe None
      result(2).lastActiveDate  shouldBe None

    }

  }

  "getServiceRepoDetailsList" should {

    "include repository with type not Deployable as services if one of the repositories with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              "some desc",
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo2",
              "some desc",
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo1",
              "some desc",
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              "some desc",
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              "some desc",
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          System.currentTimeMillis()
        )
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Service)

      result.map(_.name)          shouldBe List("repo1", "repo2")
      result.map(_.createdAt)     shouldBe List(timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp)
    }

  }

  "getAllRepositories" should {

    "deduplicate results" in {
      val teams = Seq(TeamRepositories(
        "team1",
        List(GitRepository("repo1", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
        System.currentTimeMillis()),
      TeamRepositories(
        "team2",
        List(GitRepository("repo1", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
        System.currentTimeMillis()))

      val res = TeamRepositories.getAllRepositories(teams)

      res.length shouldBe 1
    }

    "deduplicate results when there is a last modified mismatch" in {
      val teams = Seq(TeamRepositories(
        "team1",
        List(GitRepository("repo1", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
        System.currentTimeMillis()),
        TeamRepositories(
          "team2",
          List(GitRepository("repo1", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp-1000, repoType = Library, digitalServiceName = None, language = Some("Scala"), archived = false)),
          System.currentTimeMillis()))

      val res = TeamRepositories.getAllRepositories(teams)

      res.length shouldBe 1
    }
  }

  "findRepositoryDetails" should {

    "find a repository" in { // todo(konrad) add more initial TeamRepositories as test has little value
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = false,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ))

      TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(ListMap())) shouldBe defined
    }
    "find a repository where the name has a different case" in {

      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = false,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ))

      val Some(repositoryDetails) =
        TeamRepositories.findRepositoryDetails(teams, "REPO1", UrlTemplates(ListMap()))

      repositoryDetails.name       shouldBe "repo1"
      repositoryDetails.repoType   shouldBe Library
      repositoryDetails.createdAt  shouldBe timestamp
      repositoryDetails.lastActive shouldBe timestamp
    }

    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1-prototype",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          System.currentTimeMillis()
        )
      )

      val result = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(ListMap()))
      result shouldBe None
    }

  }

  "getTeamRepositoryNameList" should {

    "find the repository if the name has different case" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo2",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo1",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          System.currentTimeMillis()
        )
      )
      val result = TeamRepositories.getTeamRepositoryNameList(teams, "teamname")

      result shouldBe Some(
        Map(Service -> List("repo1", "repo2"), Library -> List("repo3"), Prototype -> List(), Other -> List()))
    }

  }

  "asRepositoryTeamNameList" should {

    "group teams by services they own filtering out any duplicates" in {

      val teams = Seq(
        TeamRepositories(
          "team1",
          List(
            GitRepository(
              "repo1",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = false,
              repoType           = Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo2",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ),
        TeamRepositories(
          "team2",
          List(
            GitRepository(
              "repo2",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ),
        TeamRepositories(
          "team2",
          List(
            GitRepository(
              "repo2",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ),
        TeamRepositories(
          "team3",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            ),
            GitRepository(
              "repo4",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        )
      )

      val result = TeamRepositories.getRepositoryToTeamNameList(teams)

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))

    }

  }

  "findTeam" should {

    val oldDeployableRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate    = 1,
      lastActiveDate = 10,
      // isInternal         = false,
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val newDeployableRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate    = 2,
      lastActiveDate = 20,
      // isInternal         = true,
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val newLibraryRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate    = 3,
      lastActiveDate = 30,
      // isInternal         = true,
      repoType           = Library,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val newOtherRepo = GitRepository(
      "repo1",
      description,
      "",
      createdDate    = 4,
      lastActiveDate = 40,
      // isInternal         = true,
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val sharedRepo = GitRepository(
      "sharedRepo1",
      description,
      "",
      createdDate    = 5,
      lastActiveDate = 50,
      // isInternal         = true,
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false
    )

    val teams = Seq(
      TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo), System.currentTimeMillis()),
      TeamRepositories(
        "teamNameOther",
        List(
          GitRepository(
            "repo3",
            description,
            "",
            createdDate    = timestamp,
            lastActiveDate = timestamp,
            // isInternal         = true,
            repoType           = Library,
            digitalServiceName = None,
            language           = Some("Scala"),
            archived           = false
          )),
        System.currentTimeMillis()
      )
    )

    "find a team" in {
      val result = TeamRepositories.findTeam(teams, "teamName", Nil)
      result shouldBe defined
    }

    "find a team when the name is of a different case" in {
      val result = TeamRepositories.findTeam(teams, "teamname", Nil)
      result          shouldBe defined
      result.get.name shouldBe "teamName"
    }

    "get the max last active and min created at for repositories with the same name" in {
      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result shouldBe Some(
        Team(
          name                     = "teamName",
          firstActiveDate          = Some(1),
          lastActiveDate           = Some(20),
          firstServiceCreationDate = Some(oldDeployableRepo.createdDate),
          repos                    = Some(Map(Service -> List("repo1"), Library -> List(), Prototype -> List(), Other -> List()))
        ))
    }

    "Include all repository types when get the max last active and min created at for team" in {

      val teams = Seq(
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo), System.currentTimeMillis()),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          System.currentTimeMillis()
        )
      )

      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result shouldBe Some(
        Team(
          "teamName",
          Some(1),
          Some(40),
          Some(oldDeployableRepo.createdDate),
          Some(
            Map(
              Service   -> List("repo1"),
              Library   -> List("repo1"),
              Prototype -> List(),
              Other     -> List("repo1")
            ))
        )
      )
    }

    "Exclude all shared repositories when calculating the min and max activity dates for a team" in {

      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo),
          System.currentTimeMillis()),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          System.currentTimeMillis()
        )
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result shouldBe Some(
        Team(
          "teamName",
          Some(1),
          Some(40),
          Some(oldDeployableRepo.createdDate),
          Some(
            Map(
              Service   -> List("repo1"),
              Library   -> List("repo1"),
              Prototype -> List(),
              Other     -> List("repo1", "sharedRepo1")
            ))
        )
      )
    }

    "populate firstServiceCreation date by looking at only the service repository" in {

      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(newDeployableRepo, oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo),
          System.currentTimeMillis()),
        TeamRepositories(
          "teamNameOther",
          List(
            GitRepository(
              "repo3",
              description,
              "",
              createdDate    = timestamp,
              lastActiveDate = timestamp,
              // isInternal         = true,
              repoType           = Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              archived           = false
            )),
          System.currentTimeMillis()
        )
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result shouldBe Some(
        Team(
          "teamName",
          Some(1),
          Some(40),
          Some(oldDeployableRepo.createdDate),
          Some(
            Map(
              Service   -> List("repo1"),
              Library   -> List("repo1"),
              Prototype -> List(),
              Other     -> List("repo1", "sharedRepo1")
            ))
        )
      )
    }

    "return None when queried with a non existing team" in {
      TeamRepositories.findTeam(teams, "nonExistingTeam", Nil) shouldBe None
    }

  }

  "allTeamsAndTheirRepositories" should {
    val repo1 = GitRepository(
      "repo1",
      description,
      "",
      createdDate        = 1,
      lastActiveDate     = 10,
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      owningTeams        = List("teamName"),
      archived           = false
    )
    val repo2 = GitRepository(
      "repo2",
      description,
      "",
      createdDate        = 1,
      lastActiveDate     = 10,
      repoType           = Service,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)

    val repo3 = GitRepository(
      "repo3",
      description,
      "",
      createdDate        = 2,
      lastActiveDate     = 20,
      repoType           = Library,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val repo4 = GitRepository(
      "repo4",
      description,
      "",
      createdDate        = 2,
      lastActiveDate     = 20,
      repoType           = Library,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)

    val repo5 = GitRepository(
      "repo5",
      description,
      "",
      createdDate        = 3,
      lastActiveDate     = 30,
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val repo6 = GitRepository(
      "repo6",
      description,
      "",
      createdDate        = 3,
      lastActiveDate     = 30,
      repoType           = Other,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)

    val repo7 = GitRepository(
      "repo7",
      description,
      "",
      createdDate        = 4,
      lastActiveDate     = 40,
      repoType           = Prototype,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)
    val repo8 = GitRepository(
      "repo8",
      description,
      "",
      createdDate        = 4,
      lastActiveDate     = 40,
      repoType           = Prototype,
      digitalServiceName = None,
      language           = Some("Scala"),
      archived           = false)

    val teams = Seq(
      TeamRepositories("teamName", List(repo1, repo2, repo3, repo4, repo5), System.currentTimeMillis()),
      TeamRepositories("teamNameOther", List(repo4, repo5, repo6, repo7, repo8), System.currentTimeMillis())
    )

    "get all teams and their repositories grouped by repo type" in {
      val result = TeamRepositories.allTeamsAndTheirRepositories(teams, Nil)

      result should contain theSameElementsAs Seq(
        Team(
          name                     = "teamName",
          firstActiveDate          = None,
          lastActiveDate           = None,
          firstServiceCreationDate = None,
          repos = Some(
            Map(
              Service   -> List("repo1", "repo2"),
              Library   -> List("repo3", "repo4"),
              Prototype -> List(),
              Other     -> List("repo5"))),
          ownedRepos = List("repo1")
        ),
        Team(
          name                     = "teamNameOther",
          firstActiveDate          = None,
          lastActiveDate           = None,
          firstServiceCreationDate = None,
          repos = Some(
            Map(
              Service   -> List(),
              Library   -> List("repo4"),
              Prototype -> List("repo7", "repo8"),
              Other     -> List("repo5", "repo6"))
          )
        )
      )
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
          createdDate        = timestamp,
          lastActiveDate     = nowInMillis,
          repoType           = Library,
          digitalServiceName = Some(digitalServiceName),
          language           = Some("Scala"),
          archived           = false
        )

      val mostRecentTimestamp = repo1.lastActiveDate + 1

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
          TeamRepositories("team1", List(repo1, repo2), 1),
          TeamRepositories("team2", List(repo3), 1),
          TeamRepositories("team3", List(repo1, repo2, repo3), 1),
          TeamRepositories("team4", List(repo2, repo4), 1)
        )

      val result = findDigitalServiceDetails(teamsAndRepositories, "DigitalService1")

      result.value.name shouldBe digitalServiceName
      result.value.repositories shouldBe Seq(
        DigitalServiceRepository(
          name          = repo1.name,
          createdAt     = repo1.createdDate,
          lastUpdatedAt = repo1.lastActiveDate,
          repoType      = repo1.repoType,
          teamNames     = Seq("team1", "team3")
        ),
        DigitalServiceRepository(
          name          = repo2.name,
          createdAt     = repo2.createdDate,
          lastUpdatedAt = repo2.lastActiveDate,
          repoType      = repo2.repoType,
          teamNames     = Seq("team1", "team3", "team4")
        )
      )
      result.value.lastUpdatedAt shouldBe mostRecentTimestamp
    }

    "find the Digital Service when the name is of a different case" in {
      val teams = Seq(
        TeamRepositories(
          "teamName",
          List(
            GitRepository(
              name               = "repo1",
              description        = description,
              url                = "n/a",
              createdDate        = timestamp,
              lastActiveDate     = nowInMillis,
              digitalServiceName = Some("DigitalService1"),
              archived           = false
            )
          ),
          System.currentTimeMillis()
        ))

      findDigitalServiceDetails(teams, "digitalservice1").value.name shouldBe "DigitalService1"
    }
  }

}
