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
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector, ServiceConfigsConnector}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship
import uk.gov.hmrc.teamsandrepositories.persistence.{RepositoriesPersistence, TeamSummaryPersistence, TestRepoRelationshipsPersistence}

import java.time.Instant
import java.time.temporal.ChronoUnit
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
    "updating teams and repositories" should {
      "assign teams to repositories" in new Setup {
        val repo1 = aRepo.copy(name = "repo-1")
        val repo2 = aRepo.copy(name = "repo-2")
        val repo3 = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] = {
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).updateRepos(argCaptor.capture())
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
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] = {
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).updateRepos(argCaptor.capture())
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
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] = {
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).updateRepos(argCaptor.capture())
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
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] = {
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos
          .map(r => (r.name, r.repoType, r.serviceType))
          .toSet shouldBe Set(
            ("other"       , RepoType.Other  , None                             )
          , ("front-route"  , RepoType.Service, Some(ServiceType.Frontend))
          , ("admin-route"  , RepoType.Service, Some(ServiceType.Frontend))
          , ("some-frontend", RepoType.Service, Some(ServiceType.Frontend))
          , ("no-rules"     , RepoType.Service, Some(ServiceType.Backend) )
          )
      }

      "assign tags (for services)" in new Setup {
        val repo1 = aRepo.copy(name = "other-repo") // Other repo type
        val repo2 = aRepo.copy(name = "admin-frontend",      repositoryYamlText = Some("type: service\nservice-type: frontend")) // Defined by name
        val repo3 = aRepo.copy(name = "repo-stub",           repositoryYamlText = Some("type: service\nservice-type: backend" )) // Defined by name
        val repo4 = aRepo.copy(name = "admin-frontend-stub", repositoryYamlText = Some("type: service\nservice-type: frontend")) // 2 tags defined by name
        val repo5 = aRepo.copy(name = "not-a-stub",          repositoryYamlText = Some("type: service\nservice-type: frontend\ntags: []")) // YAML says not a tag
        val repo6 = aRepo.copy(name = "all-tags-defined",    repositoryYamlText = Some("type: service\nservice-type: frontend\ntags: ['admin', 'api', 'stub']")) // Defined by YAML
        when(githubConnector.getTeams()).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4, repo5, repo6)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set.empty))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set.empty))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] = {
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).updateRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos
          .map(r => (r.name, r.repoType, r.serviceType, r.tags))
          .toSet shouldBe Set(
            ("other-repo"         , RepoType.Other  , None                             , None)
          , ("admin-frontend"     , RepoType.Service, Some(ServiceType.Frontend), Some(Set(Tag.AdminFrontend)))
          , ("repo-stub"          , RepoType.Service, Some(ServiceType.Backend) , Some(Set(Tag.Stub)))
          , ("admin-frontend-stub", RepoType.Service, Some(ServiceType.Frontend), Some(Set(Tag.AdminFrontend, Tag.Stub)))
          , ("not-a-stub"         , RepoType.Service, Some(ServiceType.Frontend), Some(Set.empty))
          , ("all-tags-defined"   , RepoType.Service, Some(ServiceType.Frontend), Some(Set(Tag.AdminFrontend, Tag.Api, Tag.Stub)))
          )
      }

      "update test repo relationships" in new Setup {
        val yaml: String =
          """
            |test-repositories:
            |  - repo-1-acceptance-tests
            |  - repo-1-performance-tests
            |""".stripMargin

        val repo1 = aRepo.copy(name = "repo-1", repositoryYamlText = Some(yaml))

        when(githubConnector.getTeams()).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set.empty))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set.empty))

        onTest.updateTeamsAndRepositories().futureValue

        val persistedRelationships: Seq[TestRepoRelationship] = {
          val argCaptor: ArgumentCaptor[Seq[TestRepoRelationship]] = ArgumentCaptor.forClass(classOf[Seq[TestRepoRelationship]])
          verify(relationshipsPersistence).putRelationships(any, argCaptor.capture())
          argCaptor.getValue
        }

        persistedRelationships.length shouldBe 2
        persistedRelationships should contain theSameElementsAs Seq(
          TestRepoRelationship("repo-1-acceptance-tests", "repo-1"),
          TestRepoRelationship("repo-1-performance-tests", "repo-1")
        )
      }

      "create team summaries from repos that have teams" in new Setup {
        val repo1 = aRepo.copy(name = "repo-1", pushedAt = now.minus(5, ChronoUnit.DAYS))
        val repo2 = aRepo.copy(name = "repo-2")
        val repo3 = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedTeams: List[TeamSummary] = {
          val argCaptor: ArgumentCaptor[List[TeamSummary]] = ArgumentCaptor.forClass(classOf[List[TeamSummary]])
          verify(teamsPersistence).updateTeamSummaries(argCaptor.capture())
          argCaptor.getValue
        }
        persistedTeams.length shouldBe 2
        persistedTeams        should contain theSameElementsAs List(TeamSummary("team-a", Some(now), Seq("repo-1", "repo-2")), TeamSummary("team-b", Some(now), Seq("repo-3")))
      }

      "create team summaries from teams that have no repos" in new Setup {
        val repo1 = aRepo.copy(name = "repo-1")
        val repo2 = aRepo.copy(name = "repo-2")
        val repo3 = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams()).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List.empty[GhRepository]))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List.empty[GhRepository]))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos()).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedTeams: List[TeamSummary] = {
          val argCaptor: ArgumentCaptor[List[TeamSummary]] = ArgumentCaptor.forClass(classOf[List[TeamSummary]])
          verify(teamsPersistence).updateTeamSummaries(argCaptor.capture())
          argCaptor.getValue
        }
        persistedTeams.length shouldBe 2
        persistedTeams        should contain theSameElementsAs List(TeamSummary("team-a", None, Seq.empty), TeamSummary("team-b", None, Seq.empty))
      }
    }
  }

  trait Setup {
    val reposPersistence        : RepositoriesPersistence          = mock[RepositoriesPersistence]
    val teamsPersistence        : TeamSummaryPersistence           = mock[TeamSummaryPersistence]
    val relationshipsPersistence: TestRepoRelationshipsPersistence = mock[TestRepoRelationshipsPersistence]
    val githubConnector         : GithubConnector                  = mock[GithubConnector]
    val serviceConfigsConnector : ServiceConfigsConnector          = mock[ServiceConfigsConnector]
    val timestamper             : TimeStamper                      = new TimeStamper
    val configuration           : Configuration                    = mock[Configuration]

    when(teamsPersistence.updateTeamSummaries(any)).thenReturn(Future.successful(0))
    when(reposPersistence.updateRepos(any)).thenReturn(Future.successful(0))
    when(relationshipsPersistence.putRelationships(any[String], anySeq[TestRepoRelationship])).thenReturn(Future.unit)

    val datasource = new GithubV3RepositoryDataSource(githubConnector, timestamper)

    val onTest: PersistingService =
      PersistingService(reposPersistence, teamsPersistence, relationshipsPersistence, datasource, configuration, serviceConfigsConnector)

    val now: Instant = Instant.now()

    val teamA: GhTeam = GhTeam("team-a", now)
    val teamB: GhTeam = GhTeam("team-b", now)

    val aHeuristics: RepoTypeHeuristics = RepoTypeHeuristics(
      prototypeInName     = false,
      testsInName         = false,
      hasApplicationConf  = false,
      hasDeployProperties = false,
      hasProcfile         = false,
      hasSrcMainScala     = false,
      hasSrcMainJava      = false,
      hasTags             = false
    )

    val aRepo: GhRepository = GhRepository(
      name               = "repo",
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
      repoTypeHeuristics = aHeuristics
    )
  }
}
