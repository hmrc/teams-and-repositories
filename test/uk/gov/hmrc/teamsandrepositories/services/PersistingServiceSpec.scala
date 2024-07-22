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

import org.mockito.ArgumentCaptor
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GhTeam, GithubConnector, ServiceConfigsConnector}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence, TeamSummaryPersistence, TestRepoRelationshipsPersistence}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersistingServiceSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience:

  "PersistingService" when:
    "updating teams and repositories" should:
      "assign teams to repositories" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1")
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] =
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).putRepos(argCaptor.capture())
          argCaptor.getValue

        persistedRepos.length                                         shouldBe 3
        persistedRepos.map(_.name)                                    should contain theSameElementsAs Seq("repo-1", "repo-2", "repo-3")
        persistedRepos.filter(_.teams.contains("team-a")).map(_.name) should contain theSameElementsAs Seq(
          "repo-1",
          "repo-2")
        persistedRepos.filter(_.teams.contains("team-b")).map(_.name) should contain theSameElementsAs Seq("repo-3")

      "assign multiple teams to same repository" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1")
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo2, repo3)))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] =
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).putRepos(argCaptor.capture())
          argCaptor.getValue

        persistedRepos.length                                         shouldBe 3
        persistedRepos.map(_.name)                                    should contain theSameElementsAs List("repo-1", "repo-2", "repo-3")
        persistedRepos.filter(_.teams.contains("team-a")).map(_.name) should contain theSameElementsAs Seq(
          "repo-1",
          "repo-2")
        persistedRepos.filter(_.teams.contains("team-b")).map(_.name) should contain theSameElementsAs Seq(
          "repo-2",
          "repo-3")

      "include repositories without any associated teams" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1")
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")
        val repo4: GhRepository = aRepo.copy(name = "repo-4")
        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] =
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).putRepos(argCaptor.capture())
          argCaptor.getValue
        
        persistedRepos.length      shouldBe 4
        persistedRepos.map(_.name) should contain theSameElementsAs List("repo-1", "repo-2", "repo-3", "repo-4")
        persistedRepos.map(r => r.name -> r).toMap.get("repo-4").map(_.teams) shouldBe Some(Seq.empty)

      "assign service type" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "other")                                                                           // Other repo type
        val repo2: GhRepository = aRepo.copy(name = "front-route",   repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defined by frontend routes (not name)
        val repo3: GhRepository = aRepo.copy(name = "admin-route",   repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defined by admin frontend routes
        val repo4: GhRepository = aRepo.copy(name = "some-frontend", repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defined by name
        val repo5: GhRepository = aRepo.copy(name = "no-rules",      repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true)) // Defaults to backend as not other rule satified
        when(githubConnector.getTeams).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4, repo5)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set(repo2.name)))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set(repo3.name)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] =
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).putRepos(argCaptor.capture())
          argCaptor.getValue

        persistedRepos
          .map(r => (r.name, r.repoType, r.serviceType))
          .toSet shouldBe Set(
            ("other"        , RepoType.Other  , None                      )
          , ("front-route"  , RepoType.Service, Some(ServiceType.Frontend))
          , ("admin-route"  , RepoType.Service, Some(ServiceType.Frontend))
          , ("some-frontend", RepoType.Service, Some(ServiceType.Frontend))
          , ("no-rules"     , RepoType.Service, Some(ServiceType.Backend) )
          )

      "assign tags (for services)" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "other-repo") // Other repo type
        val repo2: GhRepository = aRepo.copy(name = "admin-frontend",      repositoryYamlText = Some("type: service\nservice-type: frontend")) // Defined by name
        val repo3: GhRepository = aRepo.copy(name = "repo-stub",           repositoryYamlText = Some("type: service\nservice-type: backend" )) // Defined by name
        val repo4: GhRepository = aRepo.copy(name = "admin-frontend-stub", repositoryYamlText = Some("type: service\nservice-type: frontend")) // 2 tags defined by name
        val repo5: GhRepository = aRepo.copy(name = "not-a-stub",          repositoryYamlText = Some("type: service\nservice-type: frontend\ntags: []")) // YAML says not a tag
        val repo6: GhRepository = aRepo.copy(name = "all-tags-defined",    repositoryYamlText = Some("type: service\nservice-type: frontend\ntags: ['admin', 'api', 'stub']")) // Defined by YAML

        val repo7: GhRepository = aRepo.copy(name = "repo-built-off-platform", repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true))
        val repo8: GhRepository = aRepo.copy(name = "repo-uses-maven",         repoTypeHeuristics = aHeuristics.copy(hasApplicationConf = true, hasPomXml = true))

        when(githubConnector.getTeams).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3, repo4, repo5, repo6, repo7, repo8)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set.empty))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set.empty))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] = {
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).putRepos(argCaptor.capture())
          argCaptor.getValue
        }
        persistedRepos
          .map(r => (r.name, r.repoType, r.serviceType, r.tags))
          .toSet shouldBe Set(
            ("other-repo"              , RepoType.Other  , None                      , None)
          , ("admin-frontend"          , RepoType.Service, Some(ServiceType.Frontend), Some(Set(Tag.AdminFrontend)))
          , ("repo-stub"               , RepoType.Service, Some(ServiceType.Backend) , Some(Set(Tag.Stub)))
          , ("admin-frontend-stub"     , RepoType.Service, Some(ServiceType.Frontend), Some(Set(Tag.AdminFrontend, Tag.Stub)))
          , ("not-a-stub"              , RepoType.Service, Some(ServiceType.Frontend), Some(Set.empty))
          , ("all-tags-defined"        , RepoType.Service, Some(ServiceType.Frontend), Some(Set(Tag.AdminFrontend, Tag.Api, Tag.Stub)))
          , ("repo-built-off-platform" , RepoType.Service, Some(ServiceType.Backend),  Some(Set(Tag.BuiltOffPlatform)))
          , ("repo-uses-maven"         , RepoType.Service, Some(ServiceType.Backend),  Some(Set(Tag.Maven)))
          )

      "update test repo relationships" in new Setup:
        val yaml: String =
          """
            |test-repositories:
            |  - repo-1-acceptance-tests
            |  - repo-1-performance-tests
            |""".stripMargin

        val repo1: GhRepository = aRepo.copy(name = "repo-1", repositoryYamlText = Some(yaml))

        when(githubConnector.getTeams).thenReturn(Future.successful(Nil))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set.empty))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set.empty))

        onTest.updateTeamsAndRepositories().futureValue

        val persistedRelationships: Seq[TestRepoRelationship] =
          val argCaptor: ArgumentCaptor[Seq[TestRepoRelationship]] = ArgumentCaptor.forClass(classOf[Seq[TestRepoRelationship]])
          verify(relationshipsPersistence).putRelationships(any, argCaptor.capture())
          argCaptor.getValue

        persistedRelationships.length shouldBe 2
        persistedRelationships should contain theSameElementsAs Seq(
          TestRepoRelationship("repo-1-acceptance-tests", "repo-1"),
          TestRepoRelationship("repo-1-performance-tests", "repo-1")
        )

      "create team summaries from repos that have teams" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1", pushedAt = now.minus(5, ChronoUnit.DAYS))
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedTeams: List[TeamSummary] = {
          val argCaptor: ArgumentCaptor[List[TeamSummary]] = ArgumentCaptor.forClass(classOf[List[TeamSummary]])
          verify(teamsPersistence).updateTeamSummaries(argCaptor.capture())
          argCaptor.getValue
        }
        persistedTeams.length shouldBe 2
        persistedTeams should contain theSameElementsAs List(
          TeamSummary("team-a", Some(now), Seq("repo-1", "repo-2")),
          TeamSummary("team-b", Some(now), Seq("repo-3"))
        )

      "create team summaries from teams that have no repos" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1")
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")
        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List.empty[GhRepository]))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List.empty[GhRepository]))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedTeams: List[TeamSummary] =
          val argCaptor: ArgumentCaptor[List[TeamSummary]] = ArgumentCaptor.forClass(classOf[List[TeamSummary]])
          verify(teamsPersistence).updateTeamSummaries(argCaptor.capture())
          argCaptor.getValue

        persistedTeams.length shouldBe 2
        persistedTeams should contain theSameElementsAs List(
          TeamSummary("team-a", None, Seq.empty),
          TeamSummary("team-b", None, Seq.empty)
        )

      "create git repositories and exclude hidden teams from owning teams list" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1")
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")

        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB, teamC)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getReposForTeam(teamC)).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedRepos: Seq[GitRepository] =
          val argCaptor: ArgumentCaptor[Seq[GitRepository]] = ArgumentCaptor.forClass(classOf[Seq[GitRepository]])
          verify(reposPersistence).putRepos(argCaptor.capture())
          argCaptor.getValue

        persistedRepos.length                                                       shouldBe 3
        persistedRepos.map(r => r.name -> r).toMap.get("repo-1").map(_.owningTeams) shouldBe Some(List("team-a"))
        persistedRepos.map(r => r.name -> r).toMap.get("repo-2").map(_.owningTeams) shouldBe Some(List("team-a"))
        persistedRepos.map(r => r.name -> r).toMap.get("repo-3").map(_.owningTeams) shouldBe Some(List("team-b"))

      "create team summaries and exclude hidden teams" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1")
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")

        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB, teamC)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getReposForTeam(teamC)).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedTeams: List[TeamSummary] =
          val argCaptor: ArgumentCaptor[List[TeamSummary]] = ArgumentCaptor.forClass(classOf[List[TeamSummary]])
          verify(teamsPersistence).updateTeamSummaries(argCaptor.capture())
          argCaptor.getValue

        persistedTeams.length shouldBe 2
        persistedTeams should contain theSameElementsAs
          List(
            TeamSummary("team-a", Some(now), Seq("repo-1", "repo-2")),
            TeamSummary("team-b", Some(now), Seq("repo-3")),
          )

      "create team summaries and exclude archived repos" in new Setup:
        val repo1: GhRepository = aRepo.copy(name = "repo-1", isArchived = true)
        val repo2: GhRepository = aRepo.copy(name = "repo-2")
        val repo3: GhRepository = aRepo.copy(name = "repo-3")

        when(githubConnector.getTeams).thenReturn(Future.successful(List(teamA, teamB, teamC)))
        when(githubConnector.getReposForTeam(teamA)).thenReturn(Future.successful(List(repo1, repo2)))
        when(githubConnector.getReposForTeam(teamB)).thenReturn(Future.successful(List(repo3)))
        when(githubConnector.getReposForTeam(teamC)).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        when(serviceConfigsConnector.getFrontendServices()).thenReturn(Future.successful(Set()))
        when(serviceConfigsConnector.getAdminFrontendServices()).thenReturn(Future.successful(Set()))
        when(githubConnector.getRepos).thenReturn(Future.successful(List(repo1, repo2, repo3)))
        onTest.updateTeamsAndRepositories().futureValue

        val persistedTeams: List[TeamSummary] =
          val argCaptor: ArgumentCaptor[List[TeamSummary]] = ArgumentCaptor.forClass(classOf[List[TeamSummary]])
          verify(teamsPersistence).updateTeamSummaries(argCaptor.capture())
          argCaptor.getValue

        persistedTeams.length shouldBe 2
        persistedTeams should contain theSameElementsAs List(
          TeamSummary("team-a", Some(now), Seq("repo-2")),
          TeamSummary("team-b", Some(now), Seq("repo-3")),
        )

  trait Setup:
    val reposPersistence        : RepositoriesPersistence          = mock[RepositoriesPersistence]
    val deletedRepoPersistence  : DeletedRepositoriesPersistence   = mock[DeletedRepositoriesPersistence]
    val teamsPersistence        : TeamSummaryPersistence           = mock[TeamSummaryPersistence]
    val relationshipsPersistence: TestRepoRelationshipsPersistence = mock[TestRepoRelationshipsPersistence]
    val githubConnector         : GithubConnector                  = mock[GithubConnector]
    val serviceConfigsConnector : ServiceConfigsConnector          = mock[ServiceConfigsConnector]
    val configuration           : Configuration                    = mock[Configuration]

    val hiddenTeamName          : String                           = "hidden-team"

    when(configuration.get[Seq[String]]("hidden.teams")).thenReturn(Seq(hiddenTeamName))
    when(configuration.get[Seq[String]]("built-off-platform")).thenReturn(Seq("repo-built-off-platform"))
    when(teamsPersistence.updateTeamSummaries(any)).thenReturn(Future.unit)
    when(reposPersistence.find()).thenReturn(Future.successful(Nil))
    when(reposPersistence.putRepos(any)).thenReturn(Future.successful(0))
    when(deletedRepoPersistence.find()).thenReturn(Future.successful(Nil))
    when(deletedRepoPersistence.deleteRepos(any)).thenReturn(Future.successful(0.toLong))
    when(relationshipsPersistence.putRelationships(any[String], any[Seq[TestRepoRelationship]])).thenReturn(Future.unit)

    val onTest: PersistingService =
      PersistingService(reposPersistence, deletedRepoPersistence, teamsPersistence, relationshipsPersistence, configuration, serviceConfigsConnector, githubConnector)

    val now: Instant = Instant.now()

    val teamA: GhTeam = GhTeam("team-a",      now)
    val teamB: GhTeam = GhTeam("team-b",      now)
    val teamC: GhTeam = GhTeam("hidden-team", now)

    val aHeuristics: RepoTypeHeuristics = RepoTypeHeuristics(
      prototypeInName     = false,
      testsInName         = false,
      hasApplicationConf  = false,
      hasDeployProperties = false,
      hasProcfile         = false,
      hasSrcMainScala     = false,
      hasSrcMainJava      = false,
      hasPomXml           = false,
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
