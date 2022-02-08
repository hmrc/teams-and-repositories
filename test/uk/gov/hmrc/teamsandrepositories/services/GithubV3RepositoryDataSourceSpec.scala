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

package uk.gov.hmrc.teamsandrepositories.services

import java.time.Instant
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.{ManifestDetails, RepoTypeHeuristics}
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
  private val teamCreatedDate = Instant.parse("2019-04-01T12:00:00Z")

  trait Setup {
    val mockGithubConnector = mock[GithubConnector]

    val githubConfig: GithubConfig = mock[GithubConfig]

    val dataSource =
      new GithubV3RepositoryDataSource(
        githubConfig    = githubConfig,
        githubConnector = mockGithubConnector,
        timestampF      = timestampF,
        sharedRepos     = List("shared-repository")
      )

    val ec = dataSource.ec

    when(mockGithubConnector.getTeamDetail(any()))
      .thenAnswer { team: GhTeam => Future.successful(Some(GhTeamDetail(1, team.name, teamCreatedDate))) }

    when(githubConfig.hiddenRepositories)
      .thenReturn(testHiddenRepositories)

    when(githubConfig.hiddenTeams)
      .thenReturn(testHiddenTeams)
  }

  val teamA = GhTeam(name = "A", createdAt = teamCreatedDate)

  val dummyManifestDetails =
    ManifestDetails(
      repoType = None,
      digitalServiceName = None,
      owningTeams = Nil
    )

  val dummyRepoTypeHeuristics =
    RepoTypeHeuristics(
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
      description        = Some("some description"),
      htmlUrl            = "url_A",
      fork               = false,
      createdDate        = now,
      pushedAt           = now,
      isPrivate          = false,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main",
      branchProtection   = None,
      manifestDetails    = dummyManifestDetails,
      repoTypeHeuristics = dummyRepoTypeHeuristics
    )

  val testHiddenRepositories = List("hidden_repo1", "hidden_repo2")
  val testHiddenTeams        = List("hidden_team1", "hidden_team2")

  "GithubV3RepositoryDataSource.getTeams" should {
    "return a list of teams and data sources filtering out hidden teams" in new Setup {
      private val teamB       = GhTeam(name = "B"           , createdAt = teamCreatedDate)
      private val hiddenTeam1 = GhTeam(name = "hidden_team1", createdAt = teamCreatedDate)

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
        name               = "repo1",
        description        = Some("a test repo"),
        htmlUrl            = "http://github.com/repo1",
        fork               = false,
        createdDate        = now,
        pushedAt           = now,
        isPrivate          = false,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main",
        branchProtection   = None,
        manifestDetails    = dummyManifestDetails,
        repoTypeHeuristics = dummyRepoTypeHeuristics
      )
      private val repo2 = GhRepository(
        name               = "repo2",
        description        = Some("another test repo"),
        htmlUrl            = "http://github.com/repo2",
        fork               = false,
        createdDate        = now,
        pushedAt           = now,
        isPrivate          = false,
        language           = Some("Scala"),
        isArchived         = false,
        defaultBranch      = "main",
        branchProtection   = None,
        manifestDetails    = dummyManifestDetails,
        repoTypeHeuristics = dummyRepoTypeHeuristics
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
            name               = "hidden_repo1",
            description        = Some("some description"),
            htmlUrl            = "url_A",
            fork               = false,
            createdDate        = now,
            pushedAt           = now,
            isPrivate          = false,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            branchProtection   = None,
            manifestDetails    = dummyManifestDetails,
            repoTypeHeuristics = dummyRepoTypeHeuristics
          ),
          GhRepository(
            name               = "A_r",
            description        = Some("some description"),
            htmlUrl            = "url_A",
            fork               = false,
            createdDate        = now,
            pushedAt           = now,
            isPrivate          = false,
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main",
            branchProtection   = None,
            manifestDetails    = dummyManifestDetails,
            repoTypeHeuristics = dummyRepoTypeHeuristics
          )
        )))

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(manifestDetails = ghRepo.manifestDetails.copy(repoType = Some(RepoType.Service)))
              )
            )
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(manifestDetails =
                    ghRepo.manifestDetails.copy(digitalServiceName = Some("service-abcd")))
              )
            )
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(manifestDetails =
                    ghRepo.manifestDetails.copy(
                      repoType = Some(RepoType.Service),
                      digitalServiceName = Some("service-abcd")
                    ))
              )
            )
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(manifestDetails =
                    ghRepo.manifestDetails.copy(repoType = Some(RepoType.Library)))
              )
            )
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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

    "set type as Other if the repository.yaml does not contain a type" in new Setup {
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(manifestDetails =
                    ghRepo.manifestDetails.copy(repoType = None))
              )
            )
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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
        .thenReturn(
          Future
            .successful(
              List(
                ghRepo
                  .copy(manifestDetails =
                    ghRepo.manifestDetails.copy(
                      owningTeams = Seq("team1", "team2")
                    )
                  )
              )
            )
        )

      dataSource
        .mapTeam(teamA, persistedTeams = Seq.empty, updatedRepos = Seq.empty)
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

    "set isPrivate to true if the repo is private" in new Setup {
      val privateRepo = ghRepo.copy(isPrivate = true)
      when(mockGithubConnector.getTeams())
        .thenReturn(Future.successful(List(teamA)))

      when(mockGithubConnector.getReposForTeam(teamA))
        .thenReturn(Future.successful(List(privateRepo)))

      val repositories: TeamRepositories = dataSource
        .mapTeam(teamA, persistedTeams = Nil, updatedRepos = Seq.empty)
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

      val updatedRepo =
        repository.copy(lastActiveDate = now)

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
          ),
          updatedRepos = Seq(updatedRepo)
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
  }
}
