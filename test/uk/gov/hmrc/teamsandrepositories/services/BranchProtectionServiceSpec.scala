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

import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.teamsandrepositories.connectors.{BranchProtection, BuildDeployApiConnector, GhRepository, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

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
      when(buildDeployApiConnector.enableBranchProtection("some-repo"))
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
      when(buildDeployApiConnector.enableBranchProtection("some-repo"))
        .thenReturn(Future.failed(new Throwable("some error")))

      service
        .enableBranchProtection("some-repo")
        .failed
        .futureValue

      verifyNoMoreInteractions()
    }

    "Short-circuit and fail if the repository cannot be found on GitHub" in new Setup {
      when(buildDeployApiConnector.enableBranchProtection("some-repo"))
        .thenReturn(Future.unit)

      when(githubConnector.getRepo("some-repo"))
        .thenReturn(Future.successful(None))

      service
        .enableBranchProtection("some-repo")
        .failed
        .futureValue

      verifyNoMoreInteractions()
    }
  }

  trait Setup {
    val buildDeployApiConnector: BuildDeployApiConnector =
      mock[BuildDeployApiConnector]

    val githubConnector: GithubConnector =
      mock[GithubConnector]

    val repositoriesPersistence: RepositoriesPersistence =
      mock[RepositoriesPersistence]

    val service: BranchProtectionService =
      new BranchProtectionService(buildDeployApiConnector, githubConnector, repositoriesPersistence)
  }

  private lazy val someRepository =
    GhRepository(
      "some-repo",
      None,
      "",
      false,
      Instant.now(),
      Instant.now(),
      false,
      None,
      false,
      "main",
      Some(BranchProtection(true, true, true)),
      None,
      GhRepository.RepoTypeHeuristics(false, false, false, false, false, false, false, false)
    )
}
