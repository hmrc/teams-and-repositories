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

import java.time.{LocalDateTime, ZoneOffset}
import java.util.Date

import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.teamsandrepositories.RepoType.{Library, Other, Prototype, Service}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories.DigitalServiceRepository
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.controller.model.{Repository, RepositoryDetails, Team}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.collection.immutable.ListMap

class TeamRepositoriesSpec extends WordSpec with Matchers with OptionValues{

  val timestamp = new Date().getTime
  val now = LocalDateTime.now()
  val nowInMillis = now.toInstant(ZoneOffset.UTC).toEpochMilli


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

  private val description = "Some description"
  "getTeamList" should {

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository("repo1", "some desc", "", createdDate = 1, lastActiveDate = 10, isInternal = false, repoType = Library, digitalServiceName = None)
      val oldDeployableRepo = GitRepository("repo2", "some desc", "", createdDate = 2, lastActiveDate = 20, isInternal = false, repoType = Service, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo3", "some desc", "", createdDate = 3, lastActiveDate = 30, isInternal = true, repoType = Service, digitalServiceName = None)
      val oldOtherRepoWithLatestActiveDate = GitRepository("repo1", description, "", createdDate = 2, lastActiveDate = 40, isInternal = true, repoType = Other, digitalServiceName = None)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(newDeployableRepo, oldestLibraryRepo), System.currentTimeMillis()),
        TeamRepositories("teamName", List(oldDeployableRepo, oldOtherRepoWithLatestActiveDate), System.currentTimeMillis()),
        TeamRepositories("teamNameNotActive", List(), System.currentTimeMillis())
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

      val oldLibraryRepo = GitRepository("repo1", "some desc", "", createdDate = 2, lastActiveDate = 20, isInternal = false, repoType = Library, digitalServiceName = None)
      val oldDeployableRepo = GitRepository("repo2", "some desc", "", createdDate = 3, lastActiveDate = 30, isInternal = true, repoType = Service, digitalServiceName = None)
      val newLibraryRepo = GitRepository("repo1", "some desc", "", createdDate = 4, lastActiveDate = 40, isInternal = false, repoType = Library, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo2", "some desc", "", createdDate = 5, lastActiveDate = 50, isInternal = true, repoType = Service, digitalServiceName = None)
      val newIgnoreRepo = GitRepository("ignoreRepo", "some desc", "", createdDate = 1, lastActiveDate = 10000, isInternal = false, repoType = Service, digitalServiceName = None)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldLibraryRepo, newDeployableRepo, newIgnoreRepo), System.currentTimeMillis()),
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newIgnoreRepo), System.currentTimeMillis()),
        TeamRepositories("teamNameNotActive", List(), System.currentTimeMillis())
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
                          GitRepository("repo1", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Service, digitalServiceName = None),
                          GitRepository("repo2", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo1", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None),
                          GitRepository("repo3", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(
                          GitRepository("repo3", "some desc", "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Service)

      result.map(_.name) shouldBe List("repo1", "repo2")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp)
    }


    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val oldestLibraryRepo = GitRepository("repo1", "some desc", "", createdDate = 1, lastActiveDate = 10, isInternal = false, repoType = Library, digitalServiceName = None)
      val oldDeployableRepo = GitRepository("repo1", "some desc", "", createdDate = 2, lastActiveDate = 20, isInternal = false, repoType = Service, digitalServiceName = None)
      val newDeployableRepo = GitRepository("repo1", "some desc", "", createdDate = 3, lastActiveDate = 30, isInternal = true, repoType = Service, digitalServiceName = None)
      val newestOtherRepo = GitRepository("repo1", description, "", createdDate = 4, lastActiveDate = 40, isInternal = true, repoType = Other, digitalServiceName = None)

      val teams = Seq(
        TeamRepositories("teamNameChicken", List(oldestLibraryRepo), System.currentTimeMillis()),
        TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(newestOtherRepo), System.currentTimeMillis())
      )

      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Service)

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(1)
      result.map(_.lastUpdatedAt) shouldBe List(40)

    }
  }

  "getLibraryRepoDetailsList" should {
    "not include libraries if one of the repository with same name is Deployable" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = createdDateForDeployable1, lastActiveDate = lastActiveDateForDeployable1, isInternal = false, repoType = Service, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = createdDateForDeployable2, lastActiveDate = lastActiveDateForDeployable2, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo4", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Library)

      result.map(_.name) shouldBe List("repo3", "repo4")

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = createdDateForLib1, lastActiveDate = lastActiveDateForLib1, isInternal = false, repoType = Library, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = createdDateForLib2, lastActiveDate = lastActiveDateForLib2, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis())
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Library)

      result.map(_.name) shouldBe List("repo1")
      result.map(_.createdAt) shouldBe List(createdDateForLib1)
      result.map(_.lastUpdatedAt) shouldBe List(lastActiveDateForLib2)
    }

    "include as library even if one of the repository with same name is Other" in {

      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Other, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo4", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )
      val result: Seq[Repository] = TeamRepositories.getAllRepositories(teams).filter(_.repoType == Library)

      result.map(_.name) shouldBe List("repo1", "repo3", "repo4")
      result.map(_.createdAt) shouldBe List(timestamp, timestamp, timestamp)
      result.map(_.lastUpdatedAt) shouldBe List(timestamp, timestamp, timestamp)
    }

  }

  "findRepositoryDetails" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Library, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None)), System.currentTimeMillis())
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe Service

    }

    "calculate activity dates based on min of created and max of last active when there are multiple versions of the same repo" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = 1, lastActiveDate = 10, isInternal = false, repoType = Library, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = 2, lastActiveDate = 20, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = 3, lastActiveDate = 30, isInternal = true, repoType = Other, digitalServiceName = None)), System.currentTimeMillis())
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      val repositoryDetails: RepositoryDetails = result.get
      repositoryDetails.name shouldBe "repo1"
      repositoryDetails.repoType shouldBe Service
      repositoryDetails.createdAt shouldBe 1
      repositoryDetails.lastActive shouldBe 30

    }

    "find repository as type Library even if one of the repo with same name is not type library" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("teamNameOther1", List(GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )
      val result: Option[RepositoryDetails] = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))

      result.get.name shouldBe "repo1"
      result.get.repoType shouldBe Library
      result.get.teamNames shouldBe List("teamName", "teamNameOther", "teamNameOther1")
      result.get.githubUrls.size shouldBe 2

    }


    "not include repository with prototypes in their names" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1-prototype", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Service, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None)), System.currentTimeMillis())
      )

      val result = TeamRepositories.findRepositoryDetails(teams, "repo1", UrlTemplates(Seq(), Seq(), ListMap()))
      result shouldBe None
    }

  }


  "getTeamRepositoryNameList" should {

    "include repository with type not Deployable as services if one of the repository with same name is Deployable" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Service, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Service, digitalServiceName = None),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )
      val result = TeamRepositories.getTeamRepositoryNameList(teams, "teamName")

      result shouldBe Some(Map(Service -> List("repo1", "repo2"), Library -> List("repo3"), Prototype -> List(), Other -> List()))
    }


  }

  "asRepositoryTeamNameList" should {

    "group teams by services they own filtering out any duplicates" in {

      val teams = Seq(
        TeamRepositories("team1", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Service, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("team2", List(
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("team2", List(
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("team3", List(
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo4", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()))

      val result = TeamRepositories.getRepositoryToTeamNameList(teams)

      result should contain("repo1" -> Seq("team1"))
      result should contain("repo2" -> Seq("team1", "team2"))
      result should contain("repo3" -> Seq("team2", "team3"))
      result should contain("repo4" -> Seq("team3"))

    }

  }


  "findTeam" should {

    val oldDeployableRepo = GitRepository("repo1", description, "", createdDate = 1, lastActiveDate = 10, isInternal = false, repoType = Service, digitalServiceName = None)
    val newDeployableRepo = GitRepository("repo1", description, "", createdDate = 2, lastActiveDate = 20, isInternal = true, repoType = Service, digitalServiceName = None)
    val newLibraryRepo = GitRepository("repo1", description, "", createdDate = 3, lastActiveDate = 30, isInternal = true, repoType = Library, digitalServiceName = None)
    val newOtherRepo = GitRepository("repo1", description, "", createdDate = 4, lastActiveDate = 40, isInternal = true, repoType = Other, digitalServiceName = None)
    val sharedRepo = GitRepository("sharedRepo1", description, "", createdDate = 5, lastActiveDate = 50, isInternal = true, repoType = Other, digitalServiceName = None)

    val teams = Seq(
      TeamRepositories("teamName", List(oldDeployableRepo, newDeployableRepo), System.currentTimeMillis()),
      TeamRepositories("teamNameOther", List(GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
    )


    "get the max last active and min created at for repositories with the same name" in {
      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result shouldBe Some(Team(name = "teamName", firstActiveDate = Some(1), lastActiveDate = Some(20), firstServiceCreationDate = Some(oldDeployableRepo.createdDate),
        repos = Some(Map(
          Service -> List("repo1"),
          Library -> List(),
          Prototype -> List(),
          Other -> List())))
      )
    }

    "Include all repository types when get the max last active and min created at for team" in {

      val teams = Seq(
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )

      val result = TeamRepositories.findTeam(teams, "teamName", Nil)

      result shouldBe Some(
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Some(Map(
            Service -> List("repo1"),
            Library -> List("repo1"),
            Prototype -> List(),
            Other -> List("repo1")
          ))
        )
      )
    }

    "Exclude all shared repositories when calculating the min and max activity dates for a team" in {

      val teams = Seq(
        TeamRepositories("teamName", List(oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result shouldBe Some(
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Some(Map(
            Service -> List("repo1"),
            Library -> List("repo1"),
            Prototype -> List(),
            Other -> List("repo1", "sharedRepo1")
          ))
        )
      )
    }


    "populate firstServiceCreation date by looking at only the service repository" in {

      val teams = Seq(
        TeamRepositories("teamName", List(newDeployableRepo, oldDeployableRepo, newLibraryRepo, newOtherRepo, sharedRepo), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis())
      )

      val result = TeamRepositories.findTeam(teams, "teamName", List("sharedRepo1", "sharedRepo2", "sharedRepo3"))

      result shouldBe Some(
        Team("teamName", Some(1), Some(40), Some(oldDeployableRepo.createdDate),
          Some(Map(
            Service -> List("repo1"),
            Library -> List("repo1"),
            Prototype -> List(),
            Other -> List("repo1", "sharedRepo1")
          ))
        )
      )
    }


    "return None when queried with a non existing team" in {
      TeamRepositories.findTeam(teams, "nonExistingTeam", Nil) shouldBe None
    }

  }



  "allTeamsAndTheirRepositories" should {
    val repo1 = GitRepository("repo1", description, "", createdDate = 1, lastActiveDate = 10, repoType = Service, digitalServiceName = None)
    val repo2 = GitRepository("repo2", description, "", createdDate = 1, lastActiveDate = 10, repoType = Service, digitalServiceName = None)

    val repo3 = GitRepository("repo3", description, "", createdDate = 2, lastActiveDate = 20, repoType = Library, digitalServiceName = None)
    val repo4 = GitRepository("repo4", description, "", createdDate = 2, lastActiveDate = 20, repoType = Library, digitalServiceName = None)

    val repo5 = GitRepository("repo5", description, "", createdDate = 3, lastActiveDate = 30, repoType = Other, digitalServiceName = None)
    val repo6 = GitRepository("repo6", description, "", createdDate = 3, lastActiveDate = 30, repoType = Other, digitalServiceName = None)

    val repo7 = GitRepository("repo7", description, "", createdDate = 4, lastActiveDate = 40, repoType = Prototype, digitalServiceName = None)
    val repo8 = GitRepository("repo8", description, "", createdDate = 4, lastActiveDate = 40, repoType = Prototype, digitalServiceName = None)


    val teams = Seq(
      TeamRepositories("teamName", List(repo1, repo2, repo3, repo4, repo5), System.currentTimeMillis()),
      TeamRepositories("teamNameOther", List(repo4,repo5,repo6,repo7,repo8), System.currentTimeMillis())
    )


    "get all teams and their repositories grouped by repo type" in {
      val result = TeamRepositories.allTeamsAndTheirRepositories(teams, Nil)

      result should contain theSameElementsAs Seq(
        Team(name = "teamName", firstActiveDate = None, lastActiveDate = None, firstServiceCreationDate = None,
          repos = Some(Map(
          Service -> List("repo1", "repo2"),
          Library -> List("repo3", "repo4"),
          Prototype -> List(),
          Other -> List("repo5")))),
        Team(name = "teamNameOther",firstActiveDate = None,lastActiveDate = None,firstServiceCreationDate = None,
          repos = Some(Map(
            Service -> List(),
            Library -> List("repo4"),
            Prototype -> List("repo7", "repo8"),
            Other -> List("repo5", "repo6"))))
      )
    }

  }


  "findDigitalServiceDetails" should {

    "get the right Digital Service information" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = false, repoType = Library, digitalServiceName = Some("DigitalService1")),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Service, digitalServiceName = Some("DigitalService1"))
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Library, digitalServiceName = Some("DigitalService1")),
                          GitRepository("repo4", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Other, digitalServiceName = Some("DigitalService2"))), System.currentTimeMillis()),
        TeamRepositories("teamNameOtherOne", List(
                          GitRepository("repo5", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Library, digitalServiceName = Some("DigitalService3")),
                          GitRepository("repo6", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Other, digitalServiceName = Some("DigitalService3"))), System.currentTimeMillis())
      )
      val result: Option[TeamRepositories.DigitalService] = TeamRepositories.findDigitalServiceDetails(teams, "DigitalService1")

      result.value.name shouldBe "DigitalService1"
      result.value.repositories shouldBe Seq(
        DigitalServiceRepository("repo1", timestamp, nowInMillis, Library, Seq("teamName")),
        DigitalServiceRepository("repo2", timestamp, nowInMillis, Service, Seq("teamName")),
        DigitalServiceRepository("repo3", timestamp, nowInMillis, Library, Seq("teamNameOther"))
      )
      result.value.lastUpdatedAt shouldBe nowInMillis
    }

    "get the lastUpdated timestamp for a Digital Service" in {
      val lastUpdatedTimestamp1 = nowInMillis
      val lastUpdatedTimestamp2 = nowInMillis + 100
      val lastUpdatedTimestamp3 = nowInMillis + 200

      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = lastUpdatedTimestamp1, isInternal = false, repoType = Library, digitalServiceName = Some("DigitalService1")),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = lastUpdatedTimestamp2, isInternal = true, repoType = Service, digitalServiceName = Some("DigitalService1")),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = lastUpdatedTimestamp3, isInternal = false, repoType = Library, digitalServiceName = Some("DigitalService1"))
                        ), System.currentTimeMillis())
      )
      val result: Option[TeamRepositories.DigitalService] = TeamRepositories.findDigitalServiceDetails(teams, "DigitalService1")

      result.value.name shouldBe "DigitalService1"
      result.value.repositories should contain theSameElementsAs Seq(
        DigitalServiceRepository("repo1", timestamp, lastUpdatedTimestamp1, Library, Seq("teamName")),
        DigitalServiceRepository("repo3", timestamp, lastUpdatedTimestamp3, Library, Seq("teamName")),
        DigitalServiceRepository("repo2", timestamp, lastUpdatedTimestamp2, Service, Seq("teamName"))
      )
      result.value.lastUpdatedAt shouldBe lastUpdatedTimestamp3
    }

    "get the correct repo types for Digital Service information" in {
      val teams = Seq(
        TeamRepositories("teamName", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = false, repoType = Library, digitalServiceName = Some("DigitalService1")),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Service, digitalServiceName = Some("DigitalService1"))
                        ), System.currentTimeMillis()),
        TeamRepositories("teamNameOther", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Prototype, digitalServiceName = Some("DigitalService1")),
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = nowInMillis, isInternal = true, repoType = Other, digitalServiceName = Some("DigitalService2"))), System.currentTimeMillis())
      )
      val result: Option[TeamRepositories.DigitalService] = TeamRepositories.findDigitalServiceDetails(teams, "DigitalService1")

      result.value.name shouldBe "DigitalService1"
      result.value.repositories shouldBe Seq(
        DigitalServiceRepository("repo1", timestamp, nowInMillis, Prototype, Seq("teamName", "teamNameOther"))
      )
      result.value.lastUpdatedAt shouldBe nowInMillis
    }
  }



  "getAllRepositories" should {
    "discard duplicate repositories according to the repository type configured hierarchy" in {

      val teams = Seq(
        TeamRepositories("team1", List(
                          GitRepository("repo1", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = false, repoType = Service, digitalServiceName = None),
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("team2", List(
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("team2", List(
                          GitRepository("repo2", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None),
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None)), System.currentTimeMillis()),
        TeamRepositories("team3", List(
                          GitRepository("repo3", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Library, digitalServiceName = None),
                          GitRepository("repo4", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Other, digitalServiceName = None),
                          GitRepository("repo5-prototype", description, "", createdDate = timestamp, lastActiveDate = timestamp, isInternal = true, repoType = Prototype, digitalServiceName = None)), System.currentTimeMillis()))

      TeamRepositories.getAllRepositories(teams) shouldBe Seq(
        Repository(name = "repo1", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = Service),
        Repository(name = "repo2", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = Library),
        Repository(name = "repo3", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = Library),
        Repository(name = "repo4", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = Other),
        Repository(name = "repo5-prototype", createdAt = timestamp, lastUpdatedAt = timestamp, repoType = Prototype)
      )

    }

  }
}
