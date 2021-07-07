/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.services

import java.time.Instant

import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.githubclient._
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persistence.{MongoTeamsAndRepositoriesPersister, TeamsAndReposPersister}

import scala.concurrent.{ExecutionContext, Future}

class GithubV3RepositoryDataSourceSpec
    extends AnyWordSpec
    with ScalaFutures
    with Matchers
    with IntegrationPatience
    with MockitoSugar
    with SpanSugar
    with BeforeAndAfterEach {

  private val nowMillis = System.currentTimeMillis()
  private val now = Instant.ofEpochMilli(nowMillis)
  private val timestampF = () => now

  val mockRegistry = mock[MetricRegistry]
  val mockCounter  = mock[Counter]

  when(mockRegistry.counter(any())).thenReturn(mockCounter)

  trait Setup {
    val mockGithubConnector = mock[GithubConnector]

    private val metrics: Metrics = new Metrics() {
      override def defaultRegistry = new MetricRegistry
      override def toJson          = ???
    }

    val dataSource =
      new GithubV3RepositoryDataSource(
        githubConfig           = githubConfig,
        githubConnector        = mockGithubConnector,
        timestampF             = timestampF,
        defaultMetricsRegistry = mockRegistry,
        repositoriesToIgnore   = List("shared-repository"),
        futureHelpers          = new FutureHelpers(metrics)
      )

    val ec = dataSource.ec

    when(mockGithubConnector.repoContainsContent(anyString(), any(), anyString()))
      .thenReturn(Future.successful(false))

    when(mockGithubConnector.getFileContent(anyString(), anyString()))
      .thenReturn(Future.successful(None))

    when(mockGithubConnector.getTags(anyString(), any()))
      .thenReturn(Future.successful(List.empty))

    when(githubConfig.hiddenRepositories)
      .thenReturn(testHiddenRepositories)

    when(githubConfig.hiddenTeams)
      .thenReturn(testHiddenTeams)
  }

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams        = List("hidden_team1", "hidden_team2")

  val githubConfig: GithubConfig = mock[GithubConfig]

  private val metrics: Metrics = new Metrics() {
    override def defaultRegistry = new MetricRegistry
    override def toJson          = ???
  }

  class StubTeamsAndReposPersister extends TeamsAndReposPersister(
    mock[MongoTeamsAndRepositoriesPersister]
  ) {
    var captor: List[TeamRepositories] = Nil

    override def update(teamsAndRepositories: TeamRepositories)(implicit ec: ExecutionContext): Future[TeamRepositories] = {
      captor +:= teamsAndRepositories
      Future.successful(teamsAndRepositories)
    }
  }

  "Github v3 Data Source getTeamsWithOrgAndDataSourceDetails" should {
    "Return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val teamA       = GhTeam("A", 1)
      private val teamB       = GhTeam("B", 2)
      private val hiddenTeam1 = GhTeam("hidden_team1", 5)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(teamA, teamB, hiddenTeam1)))

      val result = dataSource.getTeamsForHmrcOrg.futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs Seq(teamA, teamB)
    }
  }

  "Github v3 Data Source getAllRepositories" should {
    "Return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val repo1 = GhRepository(
        name           = "repo1",
        description    = "a test repo",
        id             = 0,
        htmlUrl        = "http://github.com/repo1",
        fork           = false,
        createdDate    = nowMillis,
        lastActiveDate = nowMillis,
        isPrivate      = false,
        language       = "eng",
        archived       = false
      )
      private val repo2 = GhRepository(
        name           = "repo2",
        description    = "another test repo",
        id             = 0,
        htmlUrl        = "http://github.com/repo2",
        fork           = false,
        createdDate    = nowMillis,
        lastActiveDate = nowMillis,
        isPrivate      = false,
        language       = "eng",
        archived       = false
      )
      when(mockGithubConnector.getReposForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(repo1, repo2)))

      private val result = dataSource.getAllRepositories().futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs List(
        GitRepository(
          name        = "repo1",
          description = "a test repo",
          url         = "http://github.com/repo1",
          createdDate = now,
          lastActiveDate = now,
          isPrivate      = false,
          repoType       = RepoType.Other,
          digitalServiceName = None,
          owningTeams        = List(),
          language           = Some("eng"),
          archived = false
        ),
        GitRepository(
          name               = "repo2",
          description        = "another test repo",
          url                = "http://github.com/repo2",
          createdDate        = now,
          lastActiveDate     = now,
          isPrivate          = false,
          repoType           = RepoType.Other,
          digitalServiceName = None,
          owningTeams        = List(),
          language           = Some("eng"),
          archived           = false
        )
      )
    }
  }

  "Github v3 Data Source " should {
    "Set internal = true if the DataSource is marked as internal" in new Setup {
      val internalDataSource =
        new GithubV3RepositoryDataSource(
          githubConfig           = githubConfig,
          githubConnector        = mockGithubConnector,
          timestampF             = timestampF,
          defaultMetricsRegistry = mockRegistry,
          repositoriesToIgnore   = List.empty,
          futureHelpers          = new FutureHelpers(metrics)
        )

      private val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation(eqTo("hmrc")))
        .thenReturn(Future.successful(List(team)))
      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))))

      internalDataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )),
          timestampF()
        )
    }

    "Filter out repositories according to the hidden config" in new Setup {
      private val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))
      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(List(
          GhRepository("hidden_repo1", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false),
          GhRepository("A_r", "some description", 2, "url_A", false, nowMillis, nowMillis, false, "Scala", false)
        )))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false)),
          timestampF())
    }

    "Set repoType Service if the repository contains an app/application.conf folder" in new Setup {
      val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))
      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(
          List(GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false))))

      when(mockGithubConnector.getFileContent("A_r", "conf/application.conf"))
        .thenReturn(Future.successful(Some("")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false)),
          timestampF()
        )
    }

    "Set repoType Service if the repository contains a Procfile" in new Setup {
      val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))
      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(
          List(GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false))))

      when(mockGithubConnector.getFileContent("A_r", "Procfile"))
        .thenReturn(Future.successful(Some("")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false)),
          timestampF()
        )
    }

    "Set type Service if the repository contains a deploy.properties" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(
          List(GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false))))

      when(mockGithubConnector.getFileContent("A_r", "deploy.properties"))
        .thenReturn(Future.successful(Some("")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            language           = Some("Scala"),
            archived           = false)),
        timestampF()
      )
    }

    "Set type as Deployable according if the repository.yaml contains a type of 'service'" in new Setup {
      val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      when(mockGithubConnector.getFileContent("A_r", "repository.yaml"))
        .thenReturn(Future.successful(Some("type: service")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "extract digital service name from repository.yaml" in new Setup {
      val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))
      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(
            List(GhRepository("repository-xyz", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))))

      when(mockGithubConnector.getFileContent("repository-xyz", "repository.yaml"))
        .thenReturn(Future.successful(Some("digital-service: service-abcd")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "extract digital service name and repo type from repository.yaml" in new Setup {
      val team = GhTeam("A", 1)
      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("repository-xyz", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      private val manifestYaml =
        """
          |digital-service: service-abcd
          |type: service
        """.stripMargin
      when(mockGithubConnector.getFileContent("repository-xyz", "repository.yaml"))
        .thenReturn(Future.successful(Some(manifestYaml)))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "Set type as Library according if the repository.yaml contains a type of 'library'" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      when(mockGithubConnector.getFileContent("A_r", "repository.yaml"))
        .thenReturn(Future.successful(Some("type: library")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "Set type as Other if the repository.yaml contains any other value for type" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      when(mockGithubConnector.getFileContent("A_r", "repository.yaml"))
        .thenReturn(Future.successful(Some("type: somethingelse")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "Set type as Other if the repository.yaml does not contain a type" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      when(mockGithubConnector.getFileContent("A_r", "repository.yaml"))
        .thenReturn(Future.successful(Some("description: not a type")))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "Set type Library if not Service and has src/main/scala and has tags" in new Setup {
      val team = GhTeam("A", 1)
      val ghRepository = GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(List(ghRepository)))

      when(mockGithubConnector.getTags("hmrc", ghRepository))
        .thenReturn(Future.successful(List("A_r_tag")))

      when(mockGithubConnector.repoContainsContent("src/main/scala", ghRepository, "hmrc"))
        .thenReturn(Future.successful(true))

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            language           = Some("Scala"),
            archived           = false
          )
        ),
        timestampF()
      )
    }

    "Set type Library if not Service and has src/main/java and has tags" in new Setup {
      val team = GhTeam("A", 1)
      val ghRepository = GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(List(ghRepository)))

      when(mockGithubConnector.getTags("hmrc", ghRepository))
        .thenReturn(Future.successful(List("A_r_tag")))

      when(mockGithubConnector.repoContainsContent("src/main/java", ghRepository, "hmrc"))
        .thenReturn(Future.successful(true))

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            language           = Some("Scala"),
            archived           = false
          )
        ),
        timestampF()
      )
    }

    "Set type Prototype if the repository name ends in '-prototype'" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("CATO-prototype", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            language           = Some("Scala"),
            archived           = false
          )
        ),
        timestampF()
      )
    }

    "Set type Other if not Service, Library nor Prototype and no repository.yaml file" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      val repositories = dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            language           = Some("Scala"),
            archived           = false
          )
        ),
        timestampF()
      )
    }

    "Set true owning team if info is found in repository.yaml" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))
          )
        )

      val repositoryYamlContents =
        """
          owning-teams:
            - team1
            - team2
        """

      when(mockGithubConnector.getFileContent("A_r", "repository.yaml"))
        .thenReturn(Future.successful(Some(repositoryYamlContents)))

      dataSource
        .mapTeam(team, persistedTeams = Nil)
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
              language           = Some("Scala"),
              archived           = false
            )
          ),
          timestampF()
        )
    }

    "Set owning teams to an empty list if value not specified as a list" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(
          List(GhRepository("A_r", "some description", 1, "url_A", fork = false, nowMillis, nowMillis, false, "Scala", false))))

      val repositoryYamlContents =
        """
          owning-teams: not-a-list
        """
      when(mockGithubConnector.getFileContent("A_r", "repository.yaml"))
        .thenReturn(Future.successful(Some(repositoryYamlContents)))

      private val result =
        dataSource
          .mapTeam(team, persistedTeams = Nil)
          .futureValue
          .repositories
          .head
          .owningTeams

      result shouldBe Nil
    }

    "Set isPrivate to true if the repo is private" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, true, "Scala", false))
          )
        )

      val repositories: TeamRepositories = dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            language           = Some("Scala"),
            archived           = false
          )
        ),
        timestampF()
      )
    }

    "Set language to empty string if null" in new Setup {
      val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(Future.successful(List(team)))

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.successful(
            List(GhRepository("Pete_r", "some description", 1, "url_A", false, nowMillis, nowMillis, true, null, false))
          )
        )

      val repositories: TeamRepositories = dataSource
        .mapTeam(team, persistedTeams = Nil)
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
            digitalServiceName = None,
            archived           = false
          )
        ),
        timestampF()
      )
    }

    "github api for determining the repo type" should {
      val team = GhTeam("A", 1)

      "not be called" when {
        "the last updated date from github is the same as the saved one" should {
          "also repo type and digital service name should be copied from the previously persisted record)" in new Setup {
            when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
              .thenReturn(Future.successful(List(team)))

            val lastActiveDate: Long = 1234l
            when(mockGithubConnector.getReposForTeam(team))
              .thenReturn(Future.successful(List(
                GhRepository("repo-1", "some description", 1, "url_A", false, nowMillis, lastActiveDate, true, null, false))))

            val persistedTeamRepositories = TeamRepositories(
              "A",
              List(
                GitRepository(
                  "repo-1",
                  "some description",
                  "url_A",
                  now,
                  Instant.ofEpochMilli(lastActiveDate),
                  isPrivate = true,
                  RepoType.Library,
                  Some("Some Digital Service"),
                  Nil,
                  None,
                  false
                )
              ),
              now
            )

            val repositories = dataSource
              .mapTeam(team, persistedTeams = Seq(persistedTeamRepositories))
              .futureValue

            //verify
            repositories shouldBe TeamRepositories(
              "A",
              List(GitRepository(
                "repo-1",
                "some description",
                "url_A",
                now,
                Instant.ofEpochMilli(lastActiveDate),
                isPrivate          = true,
                repoType           = RepoType.Library,
                digitalServiceName = Some("Some Digital Service"),
                archived           = false
              )),
              timestampF()
            )
            verify(mockGithubConnector, never).getFileContent(any(), any())
            verify(mockGithubConnector, never).repoContainsContent(any(), any(), any())
          }
        }
      }

      "be called" when {
        "the last updated date from github is newer than the saved one" should {
          "also repo type and digital service name should be obtained from github" in new Setup {
            when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
              .thenReturn(Future.successful(List(team)))

            when(mockGithubConnector.getReposForTeam(team))
              .thenReturn(Future.successful(
                List(
                  GhRepository(
                    "repo-1",
                    "some description",
                    1,
                    "url_A",
                    false,
                    createdDate    = nowMillis,
                    lastActiveDate = nowMillis + 1,
                    true,
                    null,
                    false
                  )
                )
              ))

            private val manifestYaml =
              """
                |digital-service: service-abcd
                |type: library
              """.stripMargin

            when(mockGithubConnector.getFileContent("repo-1", "repository.yaml"))
              .thenReturn(Future.successful(Some(manifestYaml)))

            val persistedTeamRepositories = TeamRepositories(
              "A",
              List(
                GitRepository(
                  "repo-1",
                  "some description",
                  "url_A",
                  createdDate    = now,
                  lastActiveDate = now,
                  true,
                  RepoType.Library,
                  Some("Some Digital Service"),
                  Nil,
                  None,
                  false
                )
              ),
              now
            )

            println(s"Persisted: ${persistedTeamRepositories.repositories.map(_.lastActiveDate)}")

            val repositories = dataSource
              .mapTeam(team, persistedTeams = Seq(persistedTeamRepositories))
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
                  Instant.ofEpochMilli(nowMillis + 1),
                  isPrivate          = true,
                  repoType           = RepoType.Library,
                  digitalServiceName = Some("service-abcd"),
                  archived           = false
                )
              ),
              timestampF()
            )
            verify(mockGithubConnector, times(1)).getFileContent("repo-1", "repository.yaml")
            verify(mockGithubConnector, never).repoContainsContent(any(), any(), any())
          }
        }
      }

      "for a new repository" should {
        "also repo type and digital service name should be obtained from github" in new Setup {
          when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
            .thenReturn(Future.successful(List(team)))

          val lastActiveDate: Long = 1234L

          when(mockGithubConnector.getReposForTeam(team))
            .thenReturn(
              Future.successful(
                List(GhRepository("repo-1", "some description", 1, "url_A", false, nowMillis, lastActiveDate, true, null, false))
              )
            )

          private val manifestYaml =
            """
              |digital-service: service-abcd
              |type: library
            """.stripMargin
          when(mockGithubConnector.getFileContent("repo-1", "repository.yaml"))
            .thenReturn(Future.successful(Some(manifestYaml)))

          val persistedTeamRepositories = TeamRepositories("A", Nil, now)

          val repositories = dataSource
            .mapTeam(team, persistedTeams = Seq(persistedTeamRepositories))
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
                Instant.ofEpochMilli(lastActiveDate),
                isPrivate          = true,
                repoType           = RepoType.Library,
                digitalServiceName = Some("service-abcd"),
                archived           = false
              )
            ),
            timestampF()
          )
          verify(mockGithubConnector, times(1)).getFileContent("repo-1", "repository.yaml")
          verify(mockGithubConnector, never).repoContainsContent(any(), any(), any())
        }
      }
    }

    "Retry up to 5 times in the event of a failed api call" in new Setup {
      private val team = GhTeam("A", 1)

      when(mockGithubConnector.getTeamsForOrganisation("hmrc"))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(List(team))
        )

      private val repository =
        GhRepository("A_r", "some description", 1, "url_A", false, nowMillis, nowMillis, false, "Scala", false)

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(List(repository))
        )

      when(mockGithubConnector.getFileContent("A_3", "app"))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(None)
        )

      when(mockGithubConnector.getFileContent("A_r", "Procfile"))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(None)
        )

      dataSource
        .mapTeam(team, persistedTeams = Nil)
        .futureValue(Timeout(1.minute)) shouldBe
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
              language           = Some("Scala"),
              archived           = false)),
          timestampF()
        )
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
          lastActiveDate = nowMillis,
          isPrivate      = false,
          language       = null,
          archived       = false
        )

      when(mockGithubConnector.getReposForTeam(team))
        .thenReturn(Future.successful(List(githubRepository)))

      val repository = GitRepository(
        name               = "shared-repository",
        description        = "some description",
        url                = "url_A",
        createdDate        = Instant.ofEpochMilli(0L),
        lastActiveDate     = Instant.ofEpochMilli(0L),
        repoType           = RepoType.Other,
        digitalServiceName = None,
        language           = None,
        archived           = false
      )

      dataSource
        .mapTeam(
          team,
          persistedTeams = Seq(
            TeamRepositories(
              teamName     = "A",
              repositories = List(repository),
              updateDate   = Instant.ofEpochMilli(0)
            )
          )
        )
        .futureValue shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(repository.copy(lastActiveDate = now)),
        updateDate   = now
      )

      verify(mockGithubConnector).getReposForTeam(team)
      verifyNoMoreInteractions(mockGithubConnector)
    }
  }
}
