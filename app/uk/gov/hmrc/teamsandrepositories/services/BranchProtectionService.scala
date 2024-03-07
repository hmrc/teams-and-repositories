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

import cats.data.OptionT

import play.api.Logging
import uk.gov.hmrc.teamsandrepositories.connectors.{BuildDeployApiConnector, GhRepository, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.models.NoSuchRepository
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BranchProtectionService @Inject()(
  buildDeployApiConnector: BuildDeployApiConnector
, githubConnector        : GithubConnector
, repositoriesPersistence: RepositoriesPersistence
, jenkinsJobsPersistence : JenkinsJobsPersistence
) extends Logging {

  def enableBranchProtection(repoName: String)(implicit ec: ExecutionContext): Future[Unit] =
    for {
      jobs <- jenkinsJobsPersistence.findAllByRepo(repoName)
      _    <- buildDeployApiConnector.enableBranchProtection(repoName, jobs.filter(_.jobType == JenkinsJobsPersistence.JobType.PullRequest).toList)
      repo <- OptionT(githubConnector.getRepo(repoName)).getOrElseF(Future.failed[GhRepository](NoSuchRepository(repoName)))
      _    <- repositoriesPersistence.updateRepoBranchProtection(repoName, repo.branchProtection)
    } yield ()
}
