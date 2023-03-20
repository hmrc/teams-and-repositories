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

import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector, ServiceConfigsConnector}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag}
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
        val repo1 = aRepo.copy(name = "repo-1")
        val repo2 = aRepo.copy(name = "repo-2")
        val repo3 = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        onTest.updateRepositories().futureValue

        val persistedRepos = {
          val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(persister).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos.length                                         shouldBe 3
        persistedRepos.map(_.name)                                    should contain theSameElementsAs Seq("repo-1", "repo-2", "repo-3")
        persistedRepos.filter(_.teams.contains("team-a")).map(_.name) should contain theSameElementsAs Seq(
          "repo-1",
          "repo-2")
        persistedRepos.filter(_.teams.contains("team-b")).map(_.name) should contain theSameElementsAs Seq("repo-3")
      }

      "assign multiple teams to same repository" in new Setup {
        val repo1 = aRepo.copy(name = "repo-1")
        val repo2 = aRepo.copy(name = "repo-2")
        val repo3 = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo2, repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        onTest.updateRepositories().futureValue

        val persistedRepos = {
          val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(persister).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
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
        val repo1 = aRepo.copy(name = "repo-1")
        val repo2 = aRepo.copy(name = "repo-2")
        val repo3 = aRepo.copy(name = "repo-3")
        val repo4 = aRepo.copy(name = "repo-4")
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        onTest.updateRepositories().futureValue

        val persistedRepos = {
          val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(persister).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos.length      shouldBe 4
        persistedRepos.map(_.name) should contain theSameElementsAs List("repo-1", "repo-2", "repo-3", "repo-4")
        persistedRepos.map(r => r.name -> r).toMap.get("repo-4").map(_.teams) shouldBe Some(Seq.empty)
      }

      "assign service type" in new Setup {
        val repo1 = aRepo.copy(name = "other")                                                                           // Other repo type
        val repo2 = aRepo.copy(name = "front-route",   repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defined by frontend routes (not name)
        val repo3 = aRepo.copy(name = "admin-route",   repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defined by admin frontend routes
        val repo4 = aRepo.copy(name = "some-frontend", repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defined by name
        val repo5 = aRepo.copy(name = "no-rules",      repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defaults to backend as not other rule satified
        when(githubConnector.getTeams()).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4, repo5)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set(repo2.name)))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set(repo3.name)))
        onTest.updateRepositories().futureValue

        val persistedRepos = {
          val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(persister).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos
          .map(r => (r.name, r.repoType, r.serviceType))
          .toSet shouldBe(Set(
            ("other"       , RepoType.Other  , None                             )
          , ("front-route"  , RepoType.Service, Some(ServiceType.FrontendService))
          , ("admin-route"  , RepoType.Service, Some(ServiceType.FrontendService))
          , ("some-frontend", RepoType.Service, Some(ServiceType.FrontendService))
          , ("no-rules"     , RepoType.Service, Some(ServiceType.BackendService) )
          ))
      }

      "assign tags (for services)" in new Setup {
        val repo1 = aRepo.copy(name = "other-repo") // Other repo type
        val repo2 = aRepo.copy(name = "admin-frontend",      repositoryYamlText = Some("type: Service\nservice-type: FrontendService")) // Defined by name
        val repo3 = aRepo.copy(name = "repo-stub",           repositoryYamlText = Some("type: Service\nservice-type: BackendService" )) // Defined by name
        val repo4 = aRepo.copy(name = "admin-frontend-stub", repositoryYamlText = Some("type: Service\nservice-type: FrontendService")) // 2 tags defined by name
        val repo5 = aRepo.copy(name = "not-a-stub",          repositoryYamlText = Some("type: Service\nservice-type: FrontendService\ntags: []")) // YAML says not a tag
        val repo6 = aRepo.copy(name = "all-tags-defined",    repositoryYamlText = Some("type: Service\nservice-type: FrontendService\ntags: ['AdminFrontend', 'Api', 'Stub']")) // Defined by YAML
        when(githubConnector.getTeams()).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4, repo5, repo6)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set.empty))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set.empty))
        onTest.updateRepositories().futureValue

        val persistedRepos = {
          val argCaptor = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(persister).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos
          .map(r => (r.name, r.repoType, r.serviceType, r.tags))
          .toSet shouldBe(Set(
            ("other-repo"         , RepoType.Other  , None                             , None)
          , ("admin-frontend"     , RepoType.Service, Some(ServiceType.FrontendService), Some(Set(Tag.AdminFrontend)))
          , ("repo-stub"          , RepoType.Service, Some(ServiceType.BackendService) , Some(Set(Tag.Stub)))
          , ("admin-frontend-stub", RepoType.Service, Some(ServiceType.FrontendService), Some(Set(Tag.AdminFrontend, Tag.Stub)))
          , ("not-a-stub"         , RepoType.Service, Some(ServiceType.FrontendService), Some(Set.empty))
          , ("all-tags-defined"   , RepoType.Service, Some(ServiceType.FrontendService), Some(Set(Tag.AdminFrontend, Tag.Api, Tag.Stub)))
          ))
      }
    }
  }

  trait Setup {
    val githubConfig: GithubConfig         = mock[GithubConfig]
    val persister: RepositoriesPersistence = mock[RepositoriesPersistence]
    val githubConnector                    = mock[GithubConnector]
    val serviceConfigsConnector            = mock[ServiceConfigsConnector]
    val timestamper: TimeStamper           = new TimeStamper
    val configuration: Configuration       = mock[Configuration]

    when(githubConfig.hiddenTeams).thenReturn(Set.empty)
    when(githubConfig.hiddenRepositories).thenReturn(Set.empty)
    when(configuration.get[Seq[String]]("shared.repositories")).thenReturn(Seq.empty)
    when(persister.updateRepos(any)).thenReturn(Future.successful(0))

    val datasource = new GithubV3RepositoryDataSource(githubConfig, githubConnector, timestamper, configuration)

    val onTest: PersistingService =
      new PersistingService(persister, datasource, configuration, serviceConfigsConnector, githubConnector)

    val teamA: GhTeam = GhTeam("team-a", Instant.now())
    val teamB: GhTeam = GhTeam("team-b", Instant.now())

    val aHeuristics = RepoTypeHeuristics(
      prototypeInName     = false,
      testsInName         = false,
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
      repoTypeHeuristics = aHeuristics
    )
  }
}
