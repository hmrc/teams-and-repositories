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

import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.connectors.{BranchProtection, BuildDeployApiConnector, GhRepository, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.persistence.{RepositoriesPersistence, JenkinsJobsPersistence}
import uk.gov.hmrc.teamsandrepositories.models.RepoType

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class BranchProtectionServiceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with ScalaFutures {

  "enableBranchProtection" should {

    "Invoke the branch protection API and invalidate the cache with the latest view from GitHub" in new Setup {
      val buildJob = JenkinsJobsPersistence.Job(
        repoName    = "some-repo"
      , jobName     = "some-repo-job"
      , jenkinsUrl  = "http://path/to/jenkins"
      , jobType     = JenkinsJobsPersistence.JobType.Job
      , repoType    = Some(RepoType.Service)
      , latestBuild = None
      )

      val prJob = JenkinsJobsPersistence.Job(
        repoName    = "some-repo"
      , jobName     = "some-repo-pr-builder"
      , jenkinsUrl  = "http://path/to/jenkins"
      , jobType     = JenkinsJobsPersistence.JobType.PullRequest
      , repoType    = Some(RepoType.Service)
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
    }

    "Short-circuit and fail if the branch protection API invocation fails" in new Setup {

      when(jenkinsJobsPersistence.findAllByRepo("some-repo"))
        .thenReturn(Future.successful(Nil))

      when(buildDeployApiConnector.enableBranchProtection("some-repo", Nil))
        .thenReturn(Future.failed(new Throwable("some error")))

      service
        .enableBranchProtection("some-repo")
        .failed
        .futureValue

      verifyNoMoreInteractions(repositoriesPersistence)
    }

    "Short-circuit and fail if the repository cannot be found on GitHub" in new Setup {

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
    }
  }

  trait Setup {
    val buildDeployApiConnector: BuildDeployApiConnector = mock[BuildDeployApiConnector]
    val githubConnector        : GithubConnector         = mock[GithubConnector]
    val repositoriesPersistence: RepositoriesPersistence = mock[RepositoriesPersistence]
    val jenkinsJobsPersistence : JenkinsJobsPersistence  = mock[JenkinsJobsPersistence]

    val service: BranchProtectionService =
      new BranchProtectionService(buildDeployApiConnector, githubConnector, repositoriesPersistence, jenkinsJobsPersistence)
  }

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
}
