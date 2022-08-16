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

import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector, ServiceConfigsConnector}
import uk.gov.hmrc.teamsandrepositories.models.{BackendService, FrontendService, GitRepository}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersistingServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ArgumentMatchersSugar
    with ScalaFutures
    with IntegrationPatience {

  "PersistingService" when {
    "updating repositories" should {
      "assign teams to repositories" in new Setup {
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))

        onTest.updateRepositories().futureValue

        val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
        verify(persister).updateRepos(argCaptor.capture())

        val persistedRepos: Seq[GitRepository] = argCaptor.getValue

        persistedRepos.length                                         shouldBe 3
        persistedRepos.map(_.name)                                    should contain theSameElementsAs Seq("repo-1", "repo-2", "repo-3")
        persistedRepos.filter(_.teams.contains("team-a")).map(_.name) should contain theSameElementsAs Seq(
          "repo-1",
          "repo-2")
        persistedRepos.filter(_.teams.contains("team-b")).map(_.name) should contain theSameElementsAs Seq("repo-3")
      }

      "assign multiple teams to same repository" in new Setup {
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo2, repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))

        onTest.updateRepositories().futureValue

        val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
        verify(persister).updateRepos(argCaptor.capture())

        val persistedRepos: Seq[GitRepository] = argCaptor.getValue

        persistedRepos.length                                         shouldBe 3
        persistedRepos.map(_.name)                                    should contain theSameElementsAs List("repo-1", "repo-2", "repo-3")
        persistedRepos.filter(_.teams.contains("team-a")).map(_.name) should contain theSameElementsAs Seq(
          "repo-1",
          "repo-2")
        persistedRepos.filter(_.teams.contains("team-b")).map(_.name) should contain theSameElementsAs Seq(
          "repo-2",
          "repo-3")
      }

      "include repositories without any associated teams" in new Setup {
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))

        onTest.updateRepositories().futureValue

        val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
        verify(persister).updateRepos(argCaptor.capture())

        val persistedRepos: Seq[GitRepository] = argCaptor.getValue

        persistedRepos.length      shouldBe 4
        persistedRepos.map(_.name) should contain theSameElementsAs List("repo-1", "repo-2", "repo-3", "repo-4")
        persistedRepos.map(r => r.name -> r).toMap.get("repo-4").map(_.teams) shouldBe Some(Seq.empty)
      }

      "assign service type for frontend services when repo type is Service" in new Setup {
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set("repo-3", "repo-4")))

        onTest.updateRepositories().futureValue

        val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
        verify(persister).updateRepos(argCaptor.capture())

        val persistedRepos: Seq[GitRepository] = argCaptor.getValue

        persistedRepos.find(r => r.name == "repo-3").get.serviceType shouldBe Some(FrontendService)
        persistedRepos.find(r => r.name == "repo-4").get.serviceType shouldBe Some(FrontendService)
      }

      "assign service type for backend services when repo type is Service" in new Setup {
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set("repo-1", "repo-2")))

        onTest.updateRepositories().futureValue

        val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
        verify(persister).updateRepos(argCaptor.capture())

        val persistedRepos: Seq[GitRepository] = argCaptor.getValue

        persistedRepos.find(r => r.name == "repo-3").get.serviceType shouldBe Some(BackendService)
        persistedRepos.find(r => r.name == "repo-4").get.serviceType shouldBe Some(BackendService)
      }

      "assign service type of None when repo type is not Service" in new Setup {
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set("repo-3", "repo-4")))

        onTest.updateRepositories().futureValue

        val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
        verify(persister).updateRepos(argCaptor.capture())

        val persistedRepos: Seq[GitRepository] = argCaptor.getValue

        persistedRepos.find(r => r.name == "repo-1").get.serviceType shouldBe None
        persistedRepos.find(r => r.name == "repo-2").get.serviceType shouldBe None
      }
    }
  }

  trait Setup {
    val githubConfig: GithubConfig         = mock[GithubConfig]
    val persister: RepositoriesPersistence = mock[RepositoriesPersistence]
    val githubConnector                    = mock[GithubConnector]
    val serviceConfigsConnector            = mock[ServiceConfigsConnector]
    val timestamper: Timestamper           = new Timestamper
    val configuration: Configuration       = mock[Configuration]

    when(githubConfig.hiddenTeams).thenReturn(Set.empty)
    when(githubConfig.hiddenRepositories).thenReturn(Set.empty)
    when(configuration.get[Seq[String]]("shared.repositories")).thenReturn(Seq.empty)
    when(persister.updateRepos(any)).thenReturn(Future.successful(0))

    val onTest: PersistingService =
      new PersistingService(githubConfig, persister, githubConnector, timestamper, configuration, serviceConfigsConnector)

    val teamA: GhTeam = GhTeam("team-a", Instant.now())
    val teamB: GhTeam = GhTeam("team-b", Instant.now())


    val rTH = RepoTypeHeuristics(
      prototypeInName     = false,
      hasApplicationConf  = false,
      hasDeployProperties = false,
      hasProcfile         = false,
      hasSrcMainScala     = false,
      hasSrcMainJava      = false,
      hasTags             = false
    )

    val aRepo = GhRepository(
      name               = "repo",
      description        = Some("a test repo"),
      htmlUrl            = "http://github.com/repo1",
      fork               = false,
      createdDate        = Instant.now(),
      pushedAt           = Instant.now(),
      isPrivate          = false,
      language           = Some("Scala"),
      isArchived         = false,
      defaultBranch      = "main",
      branchProtection   = None,
      repositoryYamlText = None,
      repoTypeHeuristics = rTH
    )

    val repo1 = aRepo.copy(name = "repo-1")
    val repo2 = aRepo.copy(name = "repo-2")
    // Sets RepoType = Service
    val repo3 = aRepo.copy(name = "repo-3", repoTypeHeuristics = rTH.copy(hasApplicationConf = true))
    val repo4 = aRepo.copy(name = "repo-4", repoTypeHeuristics = rTH.copy(hasApplicationConf = true))
  }
}
