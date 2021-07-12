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

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.{any, anyString}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GhTeamDetail, GithubConnector}

import scala.concurrent.Future

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

  trait Setup {
    val mockGithubConnector = mock[GithubConnector]

    val githubConfig: GithubConfig = mock[GithubConfig]

    val dataSource =
      new GithubV3RepositoryDataSource(
        githubConfig           = githubConfig,
        githubConnector        = mockGithubConnector,
        timestampF             = timestampF,
        repositoriesToIgnore   = List("shared-repository")
      )

    val ec = dataSource.ec

    when(mockGithubConnector.existsContent(any(), anyString()))
      .thenReturn(Future.successful(false))

    when(mockGithubConnector.getFileContent(any(), anyString()))
      .thenReturn(Future.successful(None))

    val teamCreatedDate = Instant.parse("2019-04-01T12:00:00Z")

    when(mockGithubConnector.getTeamDetail(any()))
      .thenAnswer { team: GhTeam => Future.successful(Some(GhTeamDetail(team.id, team.name, teamCreatedDate))) }

    when(mockGithubConnector.hasTags(any()))
      .thenReturn(Future.successful(false))

    when(githubConfig.hiddenRepositories)
      .thenReturn(testHiddenRepositories)

    when(githubConfig.hiddenTeams)
      .thenReturn(testHiddenTeams)
  }

  val teamA = GhTeam(id = 1, name = "A")

  val ghRepo =
    GhRepository(
      id             = 1,
      name           = "A_r",
      description    = Some("some description"),
      htmlUrl        = "url_A",
      fork           = false,
      createdDate    = now,
      lastActiveDate = now,
      isPrivate      = false,
      language       = Some("Scala"),
      isArchived     = false,
      defaultBranch  = "main"
    )

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams        = List("hidden_team1", "hidden_team2")


  "GithubV3RepositoryDataSource.getTeams" should {
    "return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val teamB       = GhTeam(id = 2, name = "B"           )
      private val hiddenTeam1 = GhTeam(id = 5, name = "hidden_team1")

      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA, teamB, hiddenTeam1)))

      val result = dataSource.getTeams().futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs Seq(teamA, teamB)
    }
  }

  "GithubV3RepositoryDataSource.getAllRepositories" should {
    "return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val repo1 = GhRepository(
        id             = 0,
        name           = "repo1",
        description    = Some("a test repo"),
        htmlUrl        = "http://github.com/repo1",
        fork           = false,
        createdDate    = now,
        lastActiveDate = now,
        isPrivate      = false,
        language       = Some("Scala"),
        isArchived     = false,
        defaultBranch  = "main"
      )
      private val repo2 = GhRepository(
        id             = 0,
        name           = "repo2",
        description    = Some("another test repo"),
        htmlUrl        = "http://github.com/repo2",
        fork           = false,
        createdDate    = now,
        lastActiveDate = now,
        isPrivate      = false,
        language       = Some("Scala"),
        isArchived     = false,
        defaultBranch  = "main"
      )
      when(mockGithubConnector.getRepos())
        .thenReturn(Future.successful(List(repo1, repo2)))

      private val result = dataSource.getAllRepositories().futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs List(
        GitRepository(
          name               = "repo1",
          description        = "a test repo",
          url                = "http://github.com/repo1",
          createdDate        = now,
          lastActiveDate     = now,
          isPrivate          = false,
          repoType           = RepoType.Other,
          digitalServiceName = None,
          owningTeams        = List(),
          language           = Some("Scala"),
          isArchived         = false,
          defaultBranch      = "main"
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
          language           = Some("Scala"),
          isArchived         = false,
          defaultBranch      = "main"
        )
      )
    }
  }

  "GithubV3RepositoryDataSource.mapTeam" should {
    "filter out repositories according to the hidden config" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))
      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(
          GhRepository(
            id             = 1,
            name           = "hidden_repo1",
            description    = Some("some description"),
            htmlUrl        = "url_A",
            fork           = false,
            createdDate    = now,
            lastActiveDate = now,
            isPrivate      = false,
            language       = Some("Scala"),
            isArchived     = false,
            defaultBranch  = "main"
          ),
          GhRepository(
            id             = 2,
            name           = "A_r",
            description    = Some("some description"),
            htmlUrl        = "url_A",
            fork           = false,
            createdDate    = now,
            lastActiveDate = now,
            isPrivate      = false,
            language       = Some("Scala"),
            isArchived     = false,
            defaultBranch  = "main"
          )
        )))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe
        TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              isPrivate          = false,
              repoType           = RepoType.Other,
              digitalServiceName = None,
              owningTeams        = Nil,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set repoType Service if the repository contains an app/application.conf file" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))
      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo))
      )

      when(mockGithubConnector.existsContent(ghRepo, "conf/application.conf"))
        .thenReturn(Future.successful(true))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe
        TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set repoType Service if the repository contains a Procfile" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))
      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.existsContent(ghRepo, "Procfile"))
        .thenReturn(Future.successful(true))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe
        TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set type Service if the repository contains a deploy.properties" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.existsContent(ghRepo, "deploy.properties"))
        .thenReturn(Future.successful(true))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Service,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = timestampF()
      )
    }

    "set type as Deployable according if the repository.yaml contains a type of 'service'" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some("type: service")))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "extract digital service name from repository.yaml" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))
      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some("digital-service: service-abcd")))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Other,
              digitalServiceName = Some("service-abcd"),
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "extract digital service name and repo type from repository.yaml" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      private val manifestYaml =
        """
          |digital-service: service-abcd
          |type: service
        """.stripMargin
      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some(manifestYaml)))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = Some("service-abcd"),
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set type as Library according if the repository.yaml contains a type of 'library'" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some("type: library")))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set type as Other if the repository.yaml contains any other value for type" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some("type: somethingelse")))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set type as Other if the repository.yaml does not contain a type" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some("description: not a type")))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set type Library if not Service and has src/main/scala and has tags" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.hasTags(ghRepo))
        .thenReturn(Future.successful(true))

      when(mockGithubConnector.existsContent(ghRepo, "src/main/scala"))
        .thenReturn(Future.successful(true))

      val repositories = dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = timestampF()
      )
    }

    "set type Library if not Service and has src/main/java and has tags" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      when(mockGithubConnector.hasTags(ghRepo))
        .thenReturn(Future.successful(true))

      when(mockGithubConnector.existsContent(ghRepo, "src/main/java"))
        .thenReturn(Future.successful(true))

      val repositories = dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = timestampF()
      )
    }

    "set type Prototype if the repository name ends in '-prototype'" in new Setup {
      val catoRepo = ghRepo.copy(name = ghRepo.name + "-prototype")
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(catoRepo)))

      val repositories = dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r-prototype",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Prototype,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = timestampF()
      )
    }

    "set type Other if not Service, Library nor Prototype and no repository.yaml file" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      val repositories = dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = timestampF()
      )
    }

    "set true owning team if info is found in repository.yaml" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      val repositoryYamlContents =
        """
          owning-teams:
            - team1
            - team2
        """

      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some(repositoryYamlContents)))

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
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
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "set owning teams to an empty list if value not specified as a list" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      val repositoryYamlContents =
        """
          owning-teams: not-a-list
        """
      when(mockGithubConnector.getFileContent(ghRepo, "repository.yaml"))
        .thenReturn(Future.successful(Some(repositoryYamlContents)))

      private val result =
        dataSource
          .mapTeam(teamA, persistedTeams = Nil)
          .futureValue
          .repositories
          .head
          .owningTeams

      result shouldBe Nil
    }

    "set isPrivate to true if the repo is private" in new Setup {
      val privateRepo = ghRepo.copy(isPrivate = true)
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(privateRepo)))

      val repositories: TeamRepositories = dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "some description",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            isPrivate          = true,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = timestampF()
      )
    }

    "github api for determining the repo type" should {
      "not be called" when {
        "the last updated date from github is the same as the saved one" should {
          "also repo type and digital service name should be copied from the previously persisted record)" in new Setup {
            when(mockGithubConnector.getTeams())
              .thenReturn(Future.successful(List(teamA)))

            val lastActiveDate = Instant.ofEpochMilli(1234L)
            val githubRepository = ghRepo.copy(lastActiveDate = lastActiveDate)

            when(mockGithubConnector.getReposForTeam(teamA))
              .thenReturn(Future.successful(List(githubRepository)))

            val persistedTeamRepositories = TeamRepositories(
              teamName     = "A",
              repositories = List(
                GitRepository(
                  name               = "A_r",
                  description        = "some description",
                  url                = "url_A",
                  createdDate        = now,
                  lastActiveDate     = lastActiveDate,
                  isPrivate          = false,
                  repoType           = RepoType.Library,
                  digitalServiceName = Some("Some Digital Service"),
                  owningTeams        = Nil,
                  language           = Some("Scala"),
                  isArchived         = false,
                  defaultBranch      = "main"
                )
              ),
              createdDate  = Some(teamCreatedDate),
              updateDate   = now
            )

            val repositories = dataSource
              .mapTeam(teamA, persistedTeams = Seq(persistedTeamRepositories))
              .futureValue

            //verify
            repositories shouldBe TeamRepositories(
              teamName     = "A",
              repositories = List(GitRepository(
                name               = "A_r",
                description        = "some description",
                url                = "url_A",
                createdDate        = now,
                lastActiveDate     = lastActiveDate,
                isPrivate          = false,
                repoType           = RepoType.Library,
                digitalServiceName = Some("Some Digital Service"),
                language           = Some("Scala"),
                isArchived         = false,
                defaultBranch      = "main"
              )),
              createdDate  = Some(teamCreatedDate),
              updateDate   = timestampF()
            )
            verify(mockGithubConnector, never).getFileContent(any(), any())
            verify(mockGithubConnector, never).existsContent(any(), any())
          }
        }
      }

      "be called" when {
        "the last updated date from github is newer than the saved one" should {
          "also repo type and digital service name should be obtained from github" in new Setup {
            val githubRepository = ghRepo.copy(lastActiveDate = now.plusSeconds(1))
            when(mockGithubConnector.getTeams())
              .thenReturn(Future.successful(List(teamA)))

            when(mockGithubConnector.getReposForTeam(teamA))
              .thenReturn(Future.successful(List(githubRepository)))

            private val manifestYaml =
              """
                |digital-service: service-abcd
                |type: library
              """.stripMargin

            when(mockGithubConnector.getFileContent(githubRepository, "repository.yaml"))
              .thenReturn(Future.successful(Some(manifestYaml)))

            val persistedTeamRepositories = TeamRepositories(
              teamName     = "A",
              repositories = List(
                GitRepository(
                  name               = "A_r",
                  description        = "some description",
                  url                = "url_A",
                  createdDate        = now,
                  lastActiveDate     = now,
                  isPrivate          = false,
                  repoType           = RepoType.Library,
                  digitalServiceName = Some("Some Digital Service"),
                  owningTeams        = Nil,
                  language           = Some("Scala"),
                  isArchived         = false,
                  defaultBranch      = "main"
                )
              ),
              createdDate  = Some(teamCreatedDate),
              updateDate   = now
            )

            println(s"Persisted: ${persistedTeamRepositories.repositories.map(_.lastActiveDate)}")

            val repositories = dataSource
              .mapTeam(teamA, persistedTeams = Seq(persistedTeamRepositories))
              .futureValue

            //verify
            repositories shouldBe TeamRepositories(
              teamName     = "A",
              repositories = List(
                GitRepository(
                  name               = "A_r",
                  description        = "some description",
                  url                = "url_A",
                  createdDate        = now,
                  lastActiveDate     = now.plusSeconds(1),
                  isPrivate          = false,
                  repoType           = RepoType.Library,
                  digitalServiceName = Some("service-abcd"),
                  language           = Some("Scala"),
                  isArchived         = false,
                  defaultBranch      = "main"
                )
              ),
              createdDate  = Some(teamCreatedDate),
              updateDate   = timestampF()
            )
            verify(mockGithubConnector, times(1)).getFileContent(githubRepository, "repository.yaml")
            verify(mockGithubConnector, never).existsContent(any(), any())
          }
        }
      }

      "for a new repository" should {
        "also repo type and digital service name should be obtained from github" in new Setup {
          when(mockGithubConnector.getTeams())
            .thenReturn(Future.successful(List(teamA)))

          val lastActiveDate = Instant.ofEpochMilli(1234L)
          val githubRepository = ghRepo.copy(lastActiveDate = lastActiveDate)

          when(mockGithubConnector.getReposForTeam(teamA))
            .thenReturn(Future.successful(List(githubRepository)))

          private val manifestYaml =
            """
              |digital-service: service-abcd
              |type: library
            """.stripMargin
          when(mockGithubConnector.getFileContent(githubRepository, "repository.yaml"))
            .thenReturn(Future.successful(Some(manifestYaml)))

          val persistedTeamRepositories = TeamRepositories(teamName = "A", repositories = Nil, createdDate = Some(teamCreatedDate), updateDate = now)

          val repositories = dataSource
            .mapTeam(teamA, persistedTeams = Seq(persistedTeamRepositories))
            .futureValue

          //verify
          repositories shouldBe TeamRepositories(
            teamName     = "A",
            repositories = List(
              GitRepository(
                name               = "A_r",
                description        = "some description",
                url                = "url_A",
                createdDate        = now,
                lastActiveDate     = lastActiveDate,
                isPrivate          = false,
                repoType           = RepoType.Library,
                digitalServiceName = Some("service-abcd"),
                isArchived         = false,
                language           = Some("Scala"),
                defaultBranch      = "main"
              )
            ),
            createdDate  = Some(teamCreatedDate),
            updateDate   = timestampF()
          )
          verify(mockGithubConnector, times(1)).getFileContent(githubRepository, "repository.yaml")
          verify(mockGithubConnector, never).existsContent(any(), any())
        }
      }
    }

    "Retry up to 5 times in the event of a failed api call" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(List(teamA))
        )

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(List(ghRepo))
        )

      when(mockGithubConnector.getFileContent(ghRepo, "app"))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(None)
        )

      when(mockGithubConnector.getFileContent(ghRepo, "Procfile"))
        .thenReturn(
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.failed(new RuntimeException("testing retry logic")),
          Future.successful(None)
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Nil)
        .futureValue(Timeout(1.minute)) shouldBe
        TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "some description",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main"
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = timestampF()
        )
    }

    "not try to pull data from github for known shared repositories but still update the lastUpdate date" in new Setup {
      val githubRepository =
        ghRepo.copy(
          name           = "shared-repository", // GithubV3RepositoryDataSource was configured to ignore this name
          createdDate    = Instant.ofEpochMilli(0L),
          lastActiveDate = now
        )

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(githubRepository)))

      val repository = GitRepository(
        name               = "shared-repository",
        description        = "some description",
        url                = "url_A",
        createdDate        = Instant.ofEpochMilli(0L),
        lastActiveDate     = Instant.ofEpochMilli(0L),
        repoType           = RepoType.Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main"
      )

      dataSource
        .mapTeam(
          teamA,
          persistedTeams = Seq(
            TeamRepositories(
              teamName     = "A",
              repositories = List(repository),
              createdDate  = Some(teamCreatedDate),
              updateDate   = Instant.ofEpochMilli(0)
            )
          )
        )
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(repository.copy(lastActiveDate = now)),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )

      verify(mockGithubConnector).getReposForTeam(teamA)
      verifyNoMoreInteractions(mockGithubConnector)
    }
  }
}
