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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.gov.hmrc.githubclient._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoTeamsAndRepositoriesPersister, MongoUpdateTimePersister, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.services.{GithubV3RepositoryDataSource, TeamAndOrgAndDataSource}

import scala.concurrent.{ExecutionContext, Future}


class GithubV3RepositoryDataSourceSpec extends WordSpec with ScalaFutures with Matchers with DefaultPatienceConfig with MockitoSugar with SpanSugar with BeforeAndAfterEach {

  val now = new Date().getTime
  private val timestampF = () => now

  trait Setup  {
    val githubClient = mock[GithubApiClient]
    val dataSource = new GithubV3RepositoryDataSource(githubConfig, githubClient, isInternal = false, timestampF)

    when(githubClient.repoContainsContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(false))
    when(githubClient.getFileContent(anyString(),anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(None))
    when(githubClient.getTags(anyString(),anyString())(any[ExecutionContext])).thenReturn(Future.successful(List.empty))

    when(githubConfig.hiddenRepositories).thenReturn(testHiddenRepositories)
    when(githubConfig.hiddenTeams).thenReturn(testHiddenTeams)
  }


  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams = List("hidden_team1", "hidden_team2")

  val githubConfig: GithubConfig = mock[GithubConfig]

  val ec = BlockingIOExecutionContext.executionContext

  class StubTeamsAndReposPersister extends TeamsAndReposPersister(mock[MongoTeamsAndRepositoriesPersister], mock[MongoUpdateTimePersister]) {
    var captor: List[TeamRepositories] = Nil

    override def update(teamsAndRepositories: TeamRepositories): Future[TeamRepositories] = {
      captor +:= teamsAndRepositories
      Future(teamsAndRepositories)(ec)
    }

    override def getAllTeamAndRepos: Future[(Seq[TeamRepositories], Option[LocalDateTime])] =
      Future.successful((Nil, None))
  }

  "Github v3 Data Source getTeamsWithOrgAndDataSourceDetails" should {
    "Return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val hmrcOrg = GhOrganisation("HMRC", 1)
      private val ddcnOrg = GhOrganisation("DDCN", 2)

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(hmrcOrg, ddcnOrg)))
      private val teamA = GhTeam("A", 1)
      private val teamB = GhTeam("B", 2)
      private val teamC = GhTeam("C", 3)
      private val teamD = GhTeam("D", 4)
      private val hiddenTeam1 = GhTeam("hidden_team1", 5)
      private val hiddenTeam2 = GhTeam("hidden_team2", 6)

      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(teamA, teamB, hiddenTeam1)))
      when(githubClient.getTeamsForOrganisation("DDCN")(ec)).thenReturn(Future.successful(List(teamC, teamD, hiddenTeam2)))

      val result = dataSource.getTeamsWithOrgAndDataSourceDetails.futureValue

      result.size shouldBe 4
      result should contain theSameElementsAs Seq(
        TeamAndOrgAndDataSource(hmrcOrg, teamA, dataSource),
        TeamAndOrgAndDataSource(hmrcOrg, teamB, dataSource),
        TeamAndOrgAndDataSource(ddcnOrg, teamC, dataSource),
        TeamAndOrgAndDataSource(ddcnOrg, teamD, dataSource)
      )
    }


  }

  "Github v3 Data Source " should {

    "Return a list of teams and repositories, filtering out forks" in new Setup {

      private val hmrcOrg = GhOrganisation("HMRC", 1)

      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(hmrcOrg)))
      private val teamA = GhTeam("A", 1)

      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(teamA)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description",  1, "url_A", fork = false, now, now, false), GhRepository("A_r2",  "some description",  5, "url_A2", fork = true, now, now, false))))

      val eventualTeamRepositories = dataSource.mapTeam(hmrcOrg, teamA)
      eventualTeamRepositories.futureValue shouldBe
        TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, digitalServiceName = None)), timestampF())

    }

    "Set internal = true if the DataSource is marked as internal" in new Setup {

      val internalDataSource = new GithubV3RepositoryDataSource(githubConfig, githubClient, isInternal = true, timestampF)

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", fork = false, now, now, false))))

      internalDataSource.mapTeam(org, team).futureValue shouldBe
        TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, isInternal = true, digitalServiceName = None)), timestampF())
    }

    "Filter out repositories according to the hidden config" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)

      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(
        GhRepository("hidden_repo1", "some description", 1, "url_A", false, now, now, false),
        GhRepository("A_r",   "some description", 2, "url_A", false, now, now, false))))

      dataSource.mapTeam(org, team).futureValue shouldBe
        TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, digitalServiceName = None)), timestampF())
    }


    "Set repoType Service if the repository contains an app/application.conf folder" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, false))))

      when(githubClient.repoContainsContent("conf/application.conf","A_r","HMRC")(ec)).thenReturn(Future.successful(true))

      dataSource.mapTeam(org, team).futureValue shouldBe
        TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Service, digitalServiceName = None)), timestampF())
    }

    "Set repoType Service if the repository contains a Procfile" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, false))))

      when(githubClient.repoContainsContent("Procfile","A_r","HMRC")(ec)).thenReturn(Future.successful(true))

      dataSource.mapTeam(org, team).futureValue shouldBe
        TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Service, digitalServiceName = None)), timestampF())
    }

    "Set type Service if the repository contains a deploy.properties" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, false))))

      when(githubClient.repoContainsContent(same("deploy.properties"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(true))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Service, digitalServiceName = None)), timestampF())
    }

    "Set type as Deployable according if the repository.yaml contains a type of 'service'" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", fork = false, now, now, false))))

      when(githubClient.getFileContent(same("repository.yaml"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(Some("type: service")))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Service, digitalServiceName = None)), timestampF())
    }

    "extract digital service name from repository.yaml" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("repository-xyz",   "some description", 1, "url_A", fork = false, now, now, false))))

      when(githubClient.getFileContent(same("repository.yaml"),same("repository-xyz"),same("HMRC"))(same(ec))).thenReturn(Future.successful(Some("digital-service: service-abcd")))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("repository-xyz", "some description", "url_A", now, now, repoType = RepoType.Other, digitalServiceName = Some("service-abcd"))), timestampF())
    }

    "extract digital service name and repo type from repository.yaml" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("repository-xyz",   "some description", 1, "url_A", fork = false, now, now, false))))

      private val manifestYaml =
        """
          |digital-service: service-abcd
          |type: service
        """.stripMargin
      when(githubClient.getFileContent(same("repository.yaml"),same("repository-xyz"),same("HMRC"))(same(ec))).thenReturn(Future.successful(Some(manifestYaml)))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("repository-xyz", "some description", "url_A", now, now, repoType = RepoType.Service, digitalServiceName = Some("service-abcd"))), timestampF())
    }

    "Set type as Library according if the repository.yaml contains a type of 'library'" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", fork = false, now, now, false))))

      when(githubClient.getFileContent(same("repository.yaml"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(Some("type: library")))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Library, digitalServiceName = None)), timestampF())
    }

    "Set type as Other if the repository.yaml contains any other value for type" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", fork = false, now, now, false))))

      when(githubClient.getFileContent(same("repository.yaml"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(Some("type: somethingelse")))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Other, digitalServiceName = None)), timestampF())
    }

    "Set type as Other if the repository.yaml does not contain a type" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", fork = false, now, now, false))))

      when(githubClient.getFileContent(same("repository.yaml"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(Some("description: not a type")))

      dataSource.mapTeam(org, team).futureValue shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Other, digitalServiceName = None)), timestampF())
    }

    "Set type Library if not Service and has src/main/scala and has tags" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, false))))

      when(githubClient.getTags("HMRC", "A_r")(ec)).thenReturn(Future.successful(List("A_r_tag")))
      when(githubClient.repoContainsContent(same("src/main/scala"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(true))

      val repositories = dataSource.mapTeam(org, team).futureValue
      repositories shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Library, digitalServiceName = None)), timestampF())
    }

    "Set type Library if not Service and has src/main/java and has tags" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, false))))

      when(githubClient.getTags("HMRC", "A_r")(ec)).thenReturn(Future.successful(List("A_r_tag")))
      when(githubClient.repoContainsContent(same("src/main/java"),same("A_r"),same("HMRC"))(same(ec))).thenReturn(Future.successful(true))

      val repositories = dataSource.mapTeam(org, team).futureValue
      repositories shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Library, digitalServiceName = None)), timestampF())
    }

    "Set type Prototype if the repository name ends in '-prototype'" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("CATO-prototype", "some description", 1, "url_A", false, now, now, false))))

      val repositories = dataSource.mapTeam(org, team).futureValue
      repositories shouldBe TeamRepositories("A", List(GitRepository("CATO-prototype", "some description", "url_A", now, now, repoType = RepoType.Prototype, digitalServiceName = None)), timestampF())
    }

    "Set type Other if not Service, Library nor Prototype and no repository.yaml file" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, false))))

      val repositories = dataSource.mapTeam(org, team).futureValue
      repositories shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, repoType = RepoType.Other, digitalServiceName = None)), timestampF())
    }

    "Set isPrivate to true if the repo is private" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec)).thenReturn(Future.successful(List(org)))
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(GhRepository("A_r",   "some description", 1, "url_A", false, now, now, true))))

      val repositories: TeamRepositories = dataSource.mapTeam(org, team).futureValue
      repositories shouldBe TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, isPrivate = true, repoType = RepoType.Other, digitalServiceName = None)), timestampF())
    }

    "Retry up to 5 times in the event of a failed api call" in new Setup {

      private val org = GhOrganisation("HMRC", 1)
      when(githubClient.getOrganisations(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(List(org)))

      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("HMRC")(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(List(team)))

      private val repository = GhRepository("A_r", "some description", 1, "url_A", false, now, now, false)
      when(githubClient.getReposForTeam(1)(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(List(repository)))

      when(githubClient.repoContainsContent("app", "A_r", "HMRC")(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("Procfile", "A_r", "HMRC")(ec))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.failed(new RuntimeException("something went wrong")))
        .thenReturn(Future.successful(false))

      dataSource.mapTeam(org, team).futureValue(Timeout(1 minute)) shouldBe
        TeamRepositories("A", List(GitRepository("A_r", "some description", "url_A", now, now, digitalServiceName = None)), timestampF())
    }
  }
}
