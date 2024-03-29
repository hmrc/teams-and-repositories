/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector}

import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

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
  private val teamCreatedDate = Instant.parse("2019-04-01T12:00:00Z")

  private val testTimeStamper = new TimeStamper {
    override def timestampF(): Instant = now
  }

  trait Setup {
    val mockGithubConnector = mock[GithubConnector]

    val githubConfig: GithubConfig = mock[GithubConfig]

    val dataSource =
      new GithubV3RepositoryDataSource(
        githubConnector = mockGithubConnector,
        timeStamper     = testTimeStamper
      )
  }

  val teamA = GhTeam(name = "A", createdAt = teamCreatedDate)

  val dummyRepoTypeHeuristics =
    RepoTypeHeuristics(
      prototypeInName     = false,
      testsInName         = false,
      hasApplicationConf  = false,
      hasDeployProperties = false,
      hasProcfile         = false,
      hasSrcMainScala     = false,
      hasSrcMainJava      = false,
      hasTags             = false
    )

  val ghRepo =
    GhRepository(
      name               = "A_r",
      htmlUrl            = "url_A",
      fork               = false,
      createdDate        = now,
      pushedAt           = now,
      isPrivate          = false,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main",
      branchProtection   = None,
      repositoryYamlText = None,
      repoTypeHeuristics = dummyRepoTypeHeuristics
    )


  "GithubV3RepositoryDataSource.getTeams" should {
    "return a list of teams and data sources" in new Setup {
      private val teamB       = GhTeam(name = "B"           , createdAt = teamCreatedDate)

      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA, teamB)))

      val result = dataSource.getTeams().futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs Seq(teamA, teamB)
    }
  }

  "GithubV3RepositoryDataSource.getAllRepositories" should {
    "return a list of teams and data sources" in new Setup {
      private val repo1 = GhRepository(
        name               = "repo1",
        htmlUrl            = "http://github.com/repo1",
        fork               = false,
        createdDate        = now,
        pushedAt           = now,
        isPrivate          = false,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main",
        branchProtection   = None,
        repositoryYamlText = None,
        repoTypeHeuristics = dummyRepoTypeHeuristics
      )
      private val repo2 = GhRepository(
        name               = "repo2",
        htmlUrl            = "http://github.com/repo2",
        fork               = false,
        createdDate        = now,
        pushedAt           = now,
        isPrivate          = false,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main",
        branchProtection   = None,
        repositoryYamlText = Some("description: a test repo"),
        repoTypeHeuristics = dummyRepoTypeHeuristics
      )
      when(mockGithubConnector.getRepos())
        .thenReturn(Future.successful(List(repo1, repo2)))

      private val result = dataSource.getAllRepositories().futureValue

      result.size shouldBe 2
      result      should contain theSameElementsAs List(
        GitRepository(
          name               = "repo1",
          description        = "",
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
          description        = "a test repo",
          url                = "http://github.com/repo2",
          createdDate        = now,
          lastActiveDate     = now,
          isPrivate          = false,
          repoType           = RepoType.Other,
          digitalServiceName = None,
          owningTeams        = List(),
          language           = Some("Scala"),
          isArchived         = false,
          defaultBranch      = "main",
          repositoryYamlText = Some("description: a test repo"),
        )
      )
    }
  }

  "GithubV3RepositoryDataSource.getTeamRepositories" should {
    "set repoType Service if the repository contains an app/application.conf file" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))
      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repoTypeHeuristics = ghRepo.repoTypeHeuristics.copy(hasApplicationConf = true))
              )
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe
        TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "set repoType Service if the repository contains a Procfile" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))
      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repoTypeHeuristics = ghRepo.repoTypeHeuristics.copy(hasProcfile = true))
              )
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe
        TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "set type Service if the repository contains a deploy.properties" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repoTypeHeuristics = ghRepo.repoTypeHeuristics.copy(hasDeployProperties = true))
              )
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Service,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            prototypeName      = None
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "set type as Deployable according if the repository.yaml contains a type of 'service'" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repositoryYamlText = Some("type: service"))
              )
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Service,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None,
              repositoryYamlText = Some("type: service")
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "extract digital service name from repository.yaml" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repositoryYamlText = Some("digital-service: service-abcd"))
              )
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Other,
              digitalServiceName = Some("service-abcd"),
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None,
              repositoryYamlText = Some("digital-service: service-abcd")
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "extract digital service name and repo type from repository.yaml and description" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      val manifestYaml =
        """
          |digital-service: service-abcd
          |type: service
          |description: some description
        """.stripMargin

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(ghRepo.copy(repositoryYamlText = Some(manifestYaml)))
            )
        )

      dataSource
        .getTeamRepositories(teamA)
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
              defaultBranch      = "main",
              prototypeName      = None,
              repositoryYamlText = Some(manifestYaml)
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "set type as Library according if the repository.yaml contains a type of 'library'" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(ghRepo.copy(repositoryYamlText = Some("type: library")))
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Library,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None,
              repositoryYamlText = Some("type: library")
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "set type as Other if the repository.yaml does not contain a type" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(ghRepo.copy(repositoryYamlText = Some("type: somethingelse")))
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Other,
              digitalServiceName = None,
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None,
              repositoryYamlText = Some("type: somethingelse")
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "set type Library if not Service and has src/main/scala and has tags" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repoTypeHeuristics =
                    ghRepo.repoTypeHeuristics.copy(
                      hasSrcMainScala = true,
                      hasTags = true
                    )
                  )
              )
            )
        )

      val repositories = dataSource
        .getTeamRepositories(teamA)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            prototypeName      = None
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "set type Library if not Service and has src/main/java and has tags" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repoTypeHeuristics =
                    ghRepo.repoTypeHeuristics.copy(
                      hasSrcMainJava = true,
                      hasTags = true
                    )
                  )
              )
            )
        )

      val repositories = dataSource
        .getTeamRepositories(teamA)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Library,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            prototypeName      = None
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "set type Prototype if the repository name ends in '-prototype'" in new Setup {
      val catoRepo =
        ghRepo.copy(
          name = ghRepo.name + "-prototype",
          repoTypeHeuristics = ghRepo.repoTypeHeuristics.copy(prototypeInName = true)
        )
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(catoRepo)))

      val repositories = dataSource
        .getTeamRepositories(teamA)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name                 = "A_r-prototype",
            description          = "",
            url                  = "url_A",
            createdDate          = now,
            lastActiveDate       = now,
            repoType             = RepoType.Prototype,
            digitalServiceName   = None,
            language             = Some("Scala"),
            isArchived           = false,
            defaultBranch        = "main",
            prototypeName        = Some("A_r-prototype"),
            prototypeAutoPublish = None
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "set repoType as test if repoHeuristics has inferred that there are testsInName" in new Setup {
      val catoRepo =
        ghRepo.copy(
          name = ghRepo.name + "-test",
          repoTypeHeuristics = ghRepo.repoTypeHeuristics.copy(testsInName = true)
        )
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(catoRepo)))

      val repositories = dataSource
        .getTeamRepositories(teamA)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r-test",
            description        = "",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Test,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main")
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "set type Other if not Service, Library nor Prototype and no repository.yaml file" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(ghRepo)))

      val repositories = dataSource
        .getTeamRepositories(teamA)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            prototypeName      = None
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "set true owning team if info is found in repository.yaml" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      val repositoryYamlContents =
        """
          owning-teams:
            - team1
            - team2
        """

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(repositoryYamlText = Some(repositoryYamlContents))
              )
            )
        )

      dataSource
        .getTeamRepositories(teamA)
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(
            GitRepository(
              name               = "A_r",
              description        = "",
              url                = "url_A",
              createdDate        = now,
              lastActiveDate     = now,
              repoType           = RepoType.Other,
              digitalServiceName = None,
              owningTeams        = List("team1", "team2"),
              language           = Some("Scala"),
              isArchived         = false,
              defaultBranch      = "main",
              prototypeName      = None,
              repositoryYamlText = Some(repositoryYamlContents)
            )
          ),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )
    }

    "set isPrivate to true if the repo is private" in new Setup {
      val privateRepo = ghRepo.copy(isPrivate = true)
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(privateRepo)))

      val repositories: TeamRepositories = dataSource
        .getTeamRepositories(teamA)
        .futureValue

      repositories shouldBe TeamRepositories(
        teamName     = "A",
        repositories = List(
          GitRepository(
            name               = "A_r",
            description        = "",
            url                = "url_A",
            createdDate        = now,
            lastActiveDate     = now,
            isPrivate          = true,
            repoType           = RepoType.Other,
            digitalServiceName = None,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            prototypeName      = None
          )
        ),
        createdDate  = Some(teamCreatedDate),
        updateDate   = now
      )
    }

    "not update repostiories in updatedRepos list" in new Setup {
      val githubRepository =
        ghRepo.copy(
          createdDate = Instant.ofEpochMilli(0L),
          pushedAt    = now
        )

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(githubRepository)))

      val repository = GitRepository(
        name               = "A_r",
        description        = "",
        url                = "url_A",
        createdDate        = Instant.ofEpochMilli(0L),
        lastActiveDate     = Instant.ofEpochMilli(0L),
        repoType           = RepoType.Other,
        digitalServiceName = None,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main",
        prototypeName      = None
      )

      val updatedRepo =
        repository.copy(lastActiveDate = now)

      dataSource
        .getTeamRepositories(
          teamA,
          cache = Map(updatedRepo.name -> updatedRepo)
        )
        .futureValue shouldBe TeamRepositories(
          teamName     = "A",
          repositories = List(updatedRepo),
          createdDate  = Some(teamCreatedDate),
          updateDate   = now
        )

      verify(mockGithubConnector).getReposForTeam(teamA)
      verifyNoMoreInteractions(mockGithubConnector)
    }

    "ensure repositories are sorted alphabetically" in new Setup {

      val repo1 =
        GhRepository(
          name               = "b",
          htmlUrl            = "http://github.com/repo1",
          fork               = false,
          createdDate        = now,
          pushedAt           = now,
          isPrivate          = false,
          language           = Some("Scala"),
          isArchived         = false,
          defaultBranch      = "main",
          branchProtection   = None,
          repositoryYamlText = None,
          repoTypeHeuristics = dummyRepoTypeHeuristics
        )

      val repo2 =
        repo1.copy(name = "c")

      val repo3 =
        repo1.copy(name = "a")

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(repo1, repo2, repo3)))

      val result =
        dataSource
          .getTeamRepositories(teamA)
          .futureValue
          .repositories
          .map(_.name)

      result shouldBe List("a", "b", "c")

      verify(mockGithubConnector).getReposForTeam(teamA)
    }
  }
}
