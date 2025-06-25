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

package uk.gov.hmrc.teamsandrepositories.service

import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.connector.{BranchProtection, BranchProtectionRules, BuildDeployApiConnector, GhRepository, GithubConnector, RequiredStatusChecks}
import uk.gov.hmrc.teamsandrepositories.persistence.{RepositoriesPersistence, JenkinsJobsPersistence}
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence.Job
import uk.gov.hmrc.teamsandrepositories.model.RepoType

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsNull

class BranchProtectionServiceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with ScalaFutures:

  "enableBranchProtection" should:

    "Invoke the branch protection API and invalidate the cache with the latest view from GitHub" in new Setup:
      val buildJob: JenkinsJobsPersistence.Job =
        JenkinsJobsPersistence.Job(
          repoName    = "some-repo"
        , jobName     = "some-repo-job"
        , jenkinsUrl  = "http://path/to/jenkins"
        , jobType     = JenkinsJobsPersistence.JobType.Job
        , repoType    = Some(RepoType.Service)
        , testType    = None
        , latestBuild = None
        )

      val prJob: JenkinsJobsPersistence.Job =
        JenkinsJobsPersistence.Job(
          repoName    = "some-repo"
        , jobName     = "some-repo-pr-builder"
        , jenkinsUrl  = "http://path/to/jenkins"
        , jobType     = JenkinsJobsPersistence.JobType.PullRequest
        , repoType    = Some(RepoType.Service)
        , testType    = None
        , latestBuild = None
        )

      when(jenkinsJobsPersistence.findAllByRepo("some-repo"))
        .thenReturn(Future.successful(Seq(buildJob, prJob)))

      when(buildDeployApiConnector.enableBranchProtection("some-repo", List(prJob)))
        .thenReturn(Future.unit)

      when(githubConnector.getRepo("some-repo"))
        .thenReturn(Future.successful(Some(someRepository)))

      when(repositoriesPersistence.updateRepoBranchProtection("some-repo", someRepository.branchProtection))
        .thenReturn(Future.unit)

      service
        .enableBranchProtection("some-repo")
        .futureValue

      verify(repositoriesPersistence)
        .updateRepoBranchProtection("some-repo", someRepository.branchProtection)

    "Short-circuit and fail if the branch protection API invocation fails" in new Setup:

      when(jenkinsJobsPersistence.findAllByRepo("some-repo"))
        .thenReturn(Future.successful(Nil))

      when(buildDeployApiConnector.enableBranchProtection("some-repo", Nil))
        .thenReturn(Future.failed(new Throwable("some error")))

      service
        .enableBranchProtection("some-repo")
        .failed
        .futureValue

      verifyNoMoreInteractions(repositoriesPersistence)

    "Short-circuit and fail if the repository cannot be found on GitHub" in new Setup:

      when(jenkinsJobsPersistence.findAllByRepo("some-repo"))
        .thenReturn(Future.successful(Nil))

      when(buildDeployApiConnector.enableBranchProtection("some-repo", Nil))
        .thenReturn(Future.unit)

      when(githubConnector.getRepo("some-repo"))
        .thenReturn(Future.successful(None))

      service
        .enableBranchProtection("some-repo")
        .failed
        .futureValue

      verifyNoMoreInteractions(repositoriesPersistence)

  "shouldUpdateRepo" should:
    "return false when cached repo has no branch protection info" in new Setup:
      val repo = someRepository.copy(branchProtection = None).toGitRepository
      service.shouldUpdateRepo("some-pr-builder", repo) shouldBe false

    "return true when cached repo has no current required status checks" in new Setup:
      val repo = someRepository.toGitRepository
      service.shouldUpdateRepo("some-pr-builder", repo) shouldBe true

    "return false when pr builder is already a required status check" in new Setup:
      val repo = someRepository.copy(branchProtection = Some(BranchProtection(true, true, true, Seq("some-pr-builder")))).toGitRepository
      service.shouldUpdateRepo("some-pr-builder", repo) shouldBe false

    "return true when cached repo has other required status checks but not this pr builder" in new Setup:
      val repo = someRepository.copy(branchProtection = Some(BranchProtection(true, true, true, Seq("another-check")))).toGitRepository
      service.shouldUpdateRepo("some-pr-builder", repo) shouldBe true

  "updateRulesWithStatusCheck" should:
    "create a requiredStatusChecks when not currently present" in new Setup:
      val expected = someRules.copy(requiredStatusChecks = Some(RequiredStatusChecks(strict = false, List("some-pr-builder"))))
      service.updateRulesWithStatusCheck("some-pr-builder", Some(someRules)) shouldBe Some(expected)

    "append to the list of status checks when pr-builder not already listed, preserving strict" in new Setup:
      val currentChecks = RequiredStatusChecks(strict = true, List("another-check"))
      val current = someRules.copy(requiredStatusChecks = Some(currentChecks))
      val expectedChecks = currentChecks.copy(contexts = currentChecks.contexts :+ "some-pr-builder")
      val expected = someRules.copy(requiredStatusChecks = Some(expectedChecks))
      service.updateRulesWithStatusCheck("some-pr-builder", Some(current)) shouldBe Some(expected)

  trait Setup:
    val buildDeployApiConnector: BuildDeployApiConnector = mock[BuildDeployApiConnector]
    val githubConnector        : GithubConnector         = mock[GithubConnector]
    val repositoriesPersistence: RepositoriesPersistence = mock[RepositoriesPersistence]
    val jenkinsJobsPersistence : JenkinsJobsPersistence  = mock[JenkinsJobsPersistence]

    val service: BranchProtectionService =
      BranchProtectionService(buildDeployApiConnector, githubConnector, repositoriesPersistence, jenkinsJobsPersistence)

  private lazy val someRepository =
    GhRepository(
      name               = "some-repo",
      htmlUrl            = "",
      fork               = false,
      createdDate        = Instant.now(),
      pushedAt           = Instant.now(),
      isPrivate          = false,
      language           = None,
      isArchived         = false,
      defaultBranch      = "main",
      branchProtection   = Some(BranchProtection(true, true, true)),
      repositoryYamlText = None,
      repoTypeHeuristics = GhRepository.RepoTypeHeuristics(false, false, false, false, false, false, false, false, false)
    )

  private lazy val someRules =
    BranchProtectionRules(
      requiredPullRequestReviews = None,
      requiredStatusChecks = None,
      requiredSignatures = true,
      enforceAdmins = true,
      requiredLinearHistory = false,
      allowForcePushes = false,
      allowDeletions = false,
      blockCreations = false,
      requiredConversationResolution = false,
      lockBranch = false,
      allowForkSyncing = false,
      restrictions = JsNull
    )
