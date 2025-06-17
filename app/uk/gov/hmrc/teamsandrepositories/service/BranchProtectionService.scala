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
import uk.gov.hmrc.teamsandrepositories.model.NoSuchRepository
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence.{Job, JobType}
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
      prBuilders <- jenkinsJobsPersistence.findAllByJobType(JobType.PullRequest)
      updates    <- prBuilders.foldLeftM(List.empty[RuleUpdate]) { (acc, job) =>
                      (for
                         repo         <- OptionT(repositoriesPersistence.findRepo(job.repoName))
                         current      <- OptionT.liftF(githubConnector.getBranchProtectionRules(repo.name, repo.defaultBranch))
                         updatedRules =  updateRulesIfNeeded(job, current)
                       yield updatedRules.fold(acc)(rules => RuleUpdate(repo.name, job.jobName, repo.defaultBranch, rules) :: acc)
                      ).getOrElse {
                        logger.warn(s"unable to evaluate branch protection rules for ${job.repoName}")
                        acc
                      }
                    }
      _          <- updates.foldLeftM(()):
                      (_, update) =>
                        githubConnector.updateBranchProtectionRules(update.repoName, update.defaultBranch, update.updatedRules)
                          .map(_ => logger.info(s"${update.repoName}/${update.defaultBranch} branch protection rules have been updated to have ${update.jobName} as a required status check"))
      _          =  logger.info(s"Found and updated the branch protection rules of ${updates.length} repositories that had a pr builder that was not enforced as a required status check")
    yield ()

  private[service] def updateRulesIfNeeded(
    prBuilder   : Job,
    currentRules: Option[BranchProtectionRules]
  ): Option[BranchProtectionRules] =
    currentRules match
      case None =>
        logger.warn(s"skipping ${prBuilder.repoName} as no branch protection rules currently present")
        None
      case Some(rules) =>
        val needsUpdate =
          rules.requiredStatusChecks.fold(true)(!_.contexts.contains(prBuilder.jobName))

        val requireUpToDateBranch =
          rules.requiredStatusChecks.fold(false)(_.strict)
        
        Option.when(needsUpdate) {
          val updatedStatusChecks =
            rules.requiredStatusChecks.fold(
              RequiredStatusChecks(strict = requireUpToDateBranch, contexts = List(prBuilder.jobName))
            )(existing => existing.copy(contexts = (existing.contexts :+ prBuilder.jobName)))

          rules.copy(requiredStatusChecks = Some(updatedStatusChecks))
        }

  case class RuleUpdate(
    repoName     : String,
    jobName      : String,
    defaultBranch: String,
    updatedRules : BranchProtectionRules
  )
        
    

