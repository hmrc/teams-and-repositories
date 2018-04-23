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
import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.gov.hmrc.githubclient._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.controller.BlockingIOExecutionContext
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoTeamsAndRepositoriesPersister, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.services.GithubV3RepositoryDataSource
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class GithubV3RepositoryDataSourceSpec
    extends WordSpec
    with ScalaFutures
    with Matchers
    with DefaultPatienceConfig
    with MockitoSugar
    with SpanSugar
    with BeforeAndAfterEach {

  val now                = new Date().getTime
  private val timestampF = () => now

  val mockRegistry = mock[MetricRegistry]
  val mockCounter  = mock[Counter]

  when(mockRegistry.counter(ArgumentMatchers.any())).thenReturn(mockCounter)

  trait Setup {
    val githubClient = mock[GithubApiClient]
    val dataSource =
      new GithubV3RepositoryDataSource(githubConfig, githubClient, timestampF, mockRegistry, List("shared-repository"))

    when(githubClient.repoContainsContent(anyString(), anyString(), anyString())(any[ExecutionContext]))
      .thenReturn(Future.successful(false))
    when(githubClient.getFileContent(anyString(), anyString(), anyString())(any[ExecutionContext]))
      .thenReturn(Future.successful(None))
    when(githubClient.getTags(anyString(), anyString())(any[ExecutionContext]))
      .thenReturn(Future.successful(List.empty))

    when(githubConfig.hiddenRepositories).thenReturn(testHiddenRepositories)
    when(githubConfig.hiddenTeams).thenReturn(testHiddenTeams)
  }

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams        = List("hidden_team1", "hidden_team2")

  val githubConfig: GithubConfig = mock[GithubConfig]

  val ec = BlockingIOExecutionContext.executionContext

  private val metrics: Metrics = new Metrics() {
    override def defaultRegistry = new MetricRegistry
    override def toJson          = ???
  }

  class StubTeamsAndReposPersister extends TeamsAndReposPersister(mock[MongoTeamsAndRepositoriesPersister], new FutureHelpers(metrics)) {
    var captor: List[TeamRepositories] = Nil

    override def update(teamsAndRepositories: TeamRepositories): Future[TeamRepositories] = {
      captor +:= teamsAndRepositories
      Future(teamsAndRepositories)(ec)
    }

    override def getAllTeamsAndRepos: Future[Seq[TeamRepositories]] =
      Future.successful(Nil)
  }

  "Github v3 Data Source getTeamsWithOrgAndDataSourceDetails" should {
    "Return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val teamA       = GhTeam("A", 1)
      private val teamB       = GhTeam("B", 2)
      private val hiddenTeam1 = GhTeam("hidden_team1", 5)

      when(githubClient.getTeamsForOrganisation("hmrc")(ec))
        .thenReturn(Future.successful(List(teamA, teamB, hiddenTeam1)))

      val result = dataSource.getTeamsForHmrcOrg.futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs Seq(teamA, teamB)
    }

  }

  "Github v3 Data Source " should {

    "Set internal = true if the DataSource is marked as internal" in new Setup {

      val internalDataSource =
        new GithubV3RepositoryDataSource(githubConfig, githubClient, timestampF, mockRegistry, List.empty)

      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      internalDataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe
        TeamRepositories(
          "A",
          List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              digitalServiceName = None,
              language           = Some("Scala")
            )),
          timestampF()
        )
    }

    "Filter out repositories according to the hidden config" in new Setup {
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(
        GhRepository("hidden_repo1", "some description", 1, "url_A", false, now, now, false, "Scala"),
        GhRepository("A_r", "some description", 2, "url_A", false, now, now, false, "Scala")
      )))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe
        TeamRepositories(
          "A",
          List(
            GitRepository(
              "A_r",
              "some description",
              "url_A",
              now,
              now,
              digitalServiceName = None,
              language           = Some("Scala"))),
          timestampF())
    }

    "Set repoType Service if the repository contains an app/application.conf folder" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      when(githubClient.repoContainsContent("conf/application.conf", "A_r", "hmrc")(ec))
        .thenReturn(Future.successful(true))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe
        TeamRepositories(
          "A",
          List(
            GitRepository(
              "A_r",
              "some description",
              "url_A",
              now,
              now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"))),
          timestampF()
        )
    }

    "Set repoType Service if the repository contains a Procfile" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      when(githubClient.repoContainsContent("Procfile", "A_r", "hmrc")(ec)).thenReturn(Future.successful(true))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe
        TeamRepositories(
          "A",
          List(
            GitRepository(
              "A_r",
              "some description",
              "url_A",
              now,
              now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"))),
          timestampF()
        )
    }

    "Set type Service if the repository contains a deploy.properties" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      when(githubClient.repoContainsContent(same("deploy.properties"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(true))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Service,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set type as Deployable according if the repository.yaml contains a type of 'service'" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      when(githubClient.getFileContent(same("repository.yaml"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some("type: service")))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Service,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "extract digital service name from repository.yaml" in new Setup {

      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("repository-xyz", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      when(githubClient.getFileContent(same("repository.yaml"), same("repository-xyz"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some("digital-service: service-abcd")))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "repository-xyz",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Other,
            digitalServiceName = Some("service-abcd"),
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "extract digital service name and repo type from repository.yaml" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("repository-xyz", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      private val manifestYaml =
        """
          |digital-service: service-abcd
          |type: service
        """.stripMargin
      when(githubClient.getFileContent(same("repository.yaml"), same("repository-xyz"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some(manifestYaml)))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "repository-xyz",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Service,
            digitalServiceName = Some("service-abcd"),
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set type as Library according if the repository.yaml contains a type of 'library'" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      when(githubClient.getFileContent(same("repository.yaml"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some("type: library")))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set type as Other if the repository.yaml contains any other value for type" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      when(githubClient.getFileContent(same("repository.yaml"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some("type: somethingelse")))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF())
    }

    "Set type as Other if the repository.yaml does not contain a type" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      when(githubClient.getFileContent(same("repository.yaml"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some("description: not a type")))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF())
    }

    "Set type Library if not Service and has src/main/scala and has tags" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      when(githubClient.getTags("hmrc", "A_r")(ec)).thenReturn(Future.successful(List("A_r_tag")))
      when(githubClient.repoContainsContent(same("src/main/scala"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(true))

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue
      repositories shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set type Library if not Service and has src/main/java and has tags" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      when(githubClient.getTags("hmrc", "A_r")(ec)).thenReturn(Future.successful(List("A_r_tag")))
      when(githubClient.repoContainsContent(same("src/main/java"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(true))

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue
      repositories shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set type Prototype if the repository name ends in '-prototype'" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("CATO-prototype", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue
      repositories shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "CATO-prototype",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Prototype,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set type Other if not Service, Library nor Prototype and no repository.yaml file" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala"))))

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue
      repositories shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF())
    }

    "Set true owning team if info is found in repository.yaml" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      val repositoryYamlContents =
        """
          owning-teams:
            - team1
            - team2
        """
      when(githubClient.getFileContent(same("repository.yaml"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some(repositoryYamlContents)))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            name               = "A_r",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            owningTeams        = List("team1", "team2"),
            language           = Some("Scala")
          )
        ),
        timestampF()
      )
    }

    "Set owning teams to an empty list if value not specified as a list" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
        List(GhRepository("A_r", "some description", 1, "url_A", fork = false, now, now, false, "Scala"))))

      val repositoryYamlContents =
        """
          owning-teams: not-a-list
        """
      when(githubClient.getFileContent(same("repository.yaml"), same("A_r"), same("hmrc"))(same(ec)))
        .thenReturn(Future.successful(Some(repositoryYamlContents)))

      private val result =
        dataSource
          .mapTeam(team, persistedTeams = Future.successful(Nil))
          .futureValue
          .repositories
          .head
          .owningTeams

      result shouldBe Nil
    }

    "Set isPrivate to true if the repo is private" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", false, now, now, true, "Scala"))))

      val repositories: TeamRepositories = dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue
      repositories shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "A_r",
            "some description",
            "url_A",
            now,
            now,
            isPrivate          = true,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"))),
        timestampF()
      )
    }

    "Set language to empty string if null" in new Setup {
      val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))
      when(githubClient.getReposForTeam(1)(ec)).thenReturn(
        Future.successful(List(GhRepository("Pete_r", "some description", 1, "url_A", false, now, now, true, null))))

      val repositories: TeamRepositories = dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue
      repositories shouldBe TeamRepositories(
        "A",
        List(
          GitRepository(
            "Pete_r",
            "some description",
            "url_A",
            now,
            now,
            isPrivate          = true,
            repoType           = RepoType.Other,
            digitalServiceName = None)),
        timestampF())
    }

    "github api for determining the repo type" should {
      val team = GhTeam("A", 1)

      "not be called" when {
        "the last updated date from github is the same as the saved one" should {
          "also repo type and digital service name should be copied from the previously persisted record)" in new Setup {
            when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))

            val lastActiveDate: Long = 1234l
            when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
              List(GhRepository("repo-1", "some description", 1, "url_A", false, now, lastActiveDate, true, null))))

            val persistedTeamRepositories = TeamRepositories(
              "A",
              List(
                GitRepository(
                  "repo-1",
                  "some description",
                  "url_A",
                  now,
                  lastActiveDate,
                  isPrivate = true,
                  RepoType.Library,
                  Some("Some Digital Service"),
                  Nil,
                  None
                )),
              now
            )

            val repositories = dataSource
              .mapTeam(team, persistedTeams = Future.successful(Seq(persistedTeamRepositories)))
              .futureValue

            //verify
            repositories shouldBe TeamRepositories(
              "A",
              List(
                GitRepository(
                  "repo-1",
                  "some description",
                  "url_A",
                  now,
                  lastActiveDate,
                  isPrivate          = true,
                  repoType           = RepoType.Library,
                  digitalServiceName = Some("Some Digital Service")
                )),
              timestampF()
            )
            verify(githubClient, never()).getFileContent(any(), any(), any())(any())
            verify(githubClient, never()).repoContainsContent(any(), any(), any())(any())
          }
        }

      }

      "be called" when {
        "the last updated date from github is different from the saved one" should {
          "also repo type and digital service name should be obtained from github" in new Setup {
            when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))

            when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
              List(GhRepository("repo-1", "some description", 1, "url_A", false, now, now + 1, true, null))))

            private val manifestYaml =
              """
                |digital-service: service-abcd
                |type: library
              """.stripMargin

            when(githubClient.getFileContent(same("repository.yaml"), same("repo-1"), same("hmrc"))(same(ec)))
              .thenReturn(Future.successful(Some(manifestYaml)))

            val persistedTeamRepositories = TeamRepositories(
              "A",
              List(
                GitRepository(
                  "repo-1",
                  "some description",
                  "url_A",
                  now,
                  now,
                  true,
                  RepoType.Library,
                  Some("Some Digital Service"),
                  Nil,
                  None)),
              now
            )

            val repositories = dataSource
              .mapTeam(team, persistedTeams = Future.successful(Seq(persistedTeamRepositories)))
              .futureValue

            //verify
            repositories shouldBe TeamRepositories(
              "A",
              List(
                GitRepository(
                  "repo-1",
                  "some description",
                  "url_A",
                  now,
                  now + 1,
                  isPrivate          = true,
                  repoType           = RepoType.Library,
                  digitalServiceName = Some("service-abcd")
                )),
              timestampF()
            )
            verify(githubClient, times(1)).getFileContent("repository.yaml", "repo-1", "hmrc")(ec)
            verify(githubClient, never()).repoContainsContent(any(), any(), any())(any())
          }
        }
      }

      "for a new repository" should {
        "also repo type and digital service name should be obtained from github" in new Setup {
          when(githubClient.getTeamsForOrganisation("hmrc")(ec)).thenReturn(Future.successful(List(team)))

          val lastActiveDate: Long                  = 1234L
          val previousLastSuccessfulScheduledUpdate = Option.empty[Long]

          when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(
            List(GhRepository("repo-1", "some description", 1, "url_A", false, now, lastActiveDate, true, null))))

          private val manifestYaml =
            """
              |digital-service: service-abcd
              |type: library
            """.stripMargin
          when(githubClient.getFileContent(same("repository.yaml"), same("repo-1"), same("hmrc"))(same(ec)))
            .thenReturn(Future.successful(Some(manifestYaml)))

          val persistedTeamRepositories = TeamRepositories("A", Nil, now)

          val repositories = dataSource
            .mapTeam(team, persistedTeams = Future.successful(Seq(persistedTeamRepositories)))
            .futureValue

          //verify
          repositories shouldBe TeamRepositories(
            "A",
            List(
              GitRepository(
                "repo-1",
                "some description",
                "url_A",
                now,
                lastActiveDate,
                isPrivate          = true,
                repoType           = RepoType.Library,
                digitalServiceName = Some("service-abcd")
              )),
            timestampF()
          )
          verify(githubClient, times(1)).getFileContent("repository.yaml", "repo-1", "hmrc")(ec)
          verify(githubClient, never()).repoContainsContent(any(), any(), any())(any())
        }
      }

    }

    "Retry up to 5 times in the event of a failed api call" in new Setup {
      private val team = GhTeam("A", 1)
      when(githubClient.getTeamsForOrganisation("hmrc")(ec))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.successful(List(team)))

      private val repository = GhRepository("A_r", "some description", 1, "url_A", false, now, now, false, "Scala")
      when(githubClient.getReposForTeam(1)(ec))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.successful(List(repository)))

      when(githubClient.repoContainsContent("app", "A_r", "hmrc")(ec))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.successful(false))

      when(githubClient.repoContainsContent("Procfile", "A_r", "hmrc")(ec))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.failed(new RuntimeException("testing retry logic")))
        .thenReturn(Future.successful(false))

      dataSource
        .mapTeam(team, persistedTeams = Future.successful(Nil))
        .futureValue(Timeout(1 minute)) shouldBe
        TeamRepositories(
          "A",
          List(
            GitRepository(
              "A_r",
              "some description",
              "url_A",
              now,
              now,
              digitalServiceName = None,
              language           = Some("Scala"))),
          timestampF())
    }

    "Not try to pull data from github for known shared repositories but still update the lastUpdate date" in new Setup {
      val team = GhTeam("A", 1)
      val githubRepository =
        GhRepository(
          name           = "shared-repository",
          description    = "some description",
          id             = 1,
          htmlUrl        = "url_A",
          fork           = false,
          createdDate    = 0L,
          lastActiveDate = now,
          isPrivate      = false,
          language       = null
        )

      when(githubClient.getReposForTeam(1)(ec)).thenReturn(Future.successful(List(githubRepository)))

      val repository = GitRepository(
        name               = "shared-repository",
        description        = "some description",
        url                = "url_A",
        createdDate        = 0L,
        lastActiveDate     = 0L,
        repoType           = RepoType.Other,
        digitalServiceName = None,
        language           = None
      )

      dataSource
        .mapTeam(
          team,
          persistedTeams = Future.successful(
            Seq(TeamRepositories(teamName = "A", repositories = List(repository), updateDate = 0L))
          )
        )
        .futureValue shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(repository.copy(lastActiveDate = now)),
        updateDate   = now
      )

      verify(githubClient).getReposForTeam(1)(ec)
      verifyNoMoreInteractions(githubClient)
    }
  }
}
