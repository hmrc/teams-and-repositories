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

import cats.implicits.* 
import cats.data.OptionT
import play.api.Logging
import uk.gov.hmrc.teamsandrepositories.connector.{BranchProtectionRules, BuildDeployApiConnector, GhRepository, GithubConnector, RequiredStatusChecks}
import uk.gov.hmrc.teamsandrepositories.model.{GitRepository, NoSuchRepository}
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence.JobType
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BranchProtectionService @Inject()(
  buildDeployApiConnector: BuildDeployApiConnector
, githubConnector        : GithubConnector
, repositoriesPersistence: RepositoriesPersistence
, jenkinsJobsPersistence : JenkinsJobsPersistence
) extends Logging:

  def enableBranchProtection(repoName: String)(using ExecutionContext): Future[Unit] =
    for
      jobs <- jenkinsJobsPersistence.findAllByRepo(repoName)
      _    <- buildDeployApiConnector.enableBranchProtection(repoName, jobs.filter(_.jobType == JenkinsJobsPersistence.JobType.PullRequest).toList)
      repo <- OptionT(githubConnector.getRepo(repoName)).getOrElseF(Future.failed[GhRepository](NoSuchRepository(repoName)))
      _    <- repositoriesPersistence.updateRepoBranchProtection(repoName, repo.branchProtection)
    yield ()

  def enforceRequiredStatusChecks(using ExecutionContext): Future[Unit] =
    for
      prBuilders  <- jenkinsJobsPersistence.findAllByJobType(JobType.PullRequest)
      updateCount <- prBuilders.foldLeftM(0) { (count, job) =>
                      (for
                         repo         <- OptionT(repositoriesPersistence.findRepo(job.repoName))
                         if shouldUpdateRepo(job.jobName, repo)
                         currentRules <- OptionT.liftF(githubConnector.getBranchProtectionRules(repo.name, repo.defaultBranch))
                         updatedRules <- OptionT.fromOption[Future](updateRulesWithStatusCheck(job.jobName, currentRules))
                         _            <- OptionT.liftF(githubConnector.updateBranchProtectionRules(repo.name, repo.defaultBranch, updatedRules))
                         _            =  logger.info(s"${repo.name}/${repo.defaultBranch} branch protection rules have been updated to have ${job.jobName} as a required status check")
                       yield count + 1
                      ).value.map(_.getOrElse(count)).recover {
                        case ex =>
                          logger.warn(s"unable to evaluate branch protection rules for ${job.repoName}: ${ex.getMessage}")
                          count
                      }
                    }
      _           =  logger.info(s"Found and updated the branch protection rules of $updateCount repositories that had a pr builder that was not enforced as a required status check")
    yield ()

  private[service] def shouldUpdateRepo(jobName: String, repo: GitRepository): Boolean =
    repo.branchProtection.fold(false):
      branchProtection =>
        !branchProtection.requiredStatusChecks.contains(jobName)
    
  private[service] def updateRulesWithStatusCheck(
    jobName: String,
    currentRules: Option[BranchProtectionRules]
  ): Option[BranchProtectionRules] =
    currentRules.map:
      rules =>
        val updatedStatusChecks = rules.requiredStatusChecks.fold(
          RequiredStatusChecks(strict = false, contexts = List(jobName))
        )(existing => existing.copy(contexts = existing.contexts :+ jobName))

        rules.copy(requiredStatusChecks = Some(updatedStatusChecks))

