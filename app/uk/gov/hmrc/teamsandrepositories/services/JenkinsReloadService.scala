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

import uk.gov.hmrc.teamsandrepositories.connectors.{BuildDeployApiConnector, JenkinsConnector}
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}
import uk.gov.hmrc.teamsandrepositories.models.RepoType

import cats.implicits._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsReloadService @Inject()(
  jenkinsConnector       : JenkinsConnector
, buildDeployApiConnector: BuildDeployApiConnector
, jenkinsJobsPersistence : JenkinsJobsPersistence
, repositoriesPersistence: RepositoriesPersistence
):

  private val RepoNameRegex = """.*hmrc/([^/]+)\.git""".r
  def updateBuildAndPerformanceJobs()(using ExecutionContext): Future[Unit] =
    for
      buildJobs       <- jenkinsConnector.findBuildJobs()
      performanceJobs <- jenkinsConnector.findPerformanceJobs()
      jobs            <- (buildJobs ++ performanceJobs)
                            .map(job => (job, job.gitHubUrl))
                            .collect { case (job, Some(RepoNameRegex(repoName))) => (job, repoName) }
                            .foldLeftM[Future, List[JenkinsJobsPersistence.Job]](List.empty) { case (acc, (job, repoName)) =>
                              repositoriesPersistence.findRepo(repoName).map(_.fold(acc)(repo =>
                                JenkinsJobsPersistence.Job(
                                  repoName    = repo.name
                                , jobName     = job.name
                                , jobType     = if job.name.endsWith("-pr-builder") then JenkinsJobsPersistence.JobType.PullRequest
                                                else if repo.repoType == RepoType.Test then JenkinsJobsPersistence.JobType.Test
                                                else                                     JenkinsJobsPersistence.JobType.Job
                                , testType    = repo.testType
                                , jenkinsUrl  = job.jenkinsUrl
                                , repoType    = Some(repo.repoType)
                                , latestBuild = job.latestBuild
                                ) :: acc
                              ))
                            }
      updatedJobs     <- jobs.foldLeftM[Future, List[JenkinsJobsPersistence.Job]](List.empty): (acc, job) =>
                           if job.jobType == JenkinsJobsPersistence.JobType.Test then 
                             jenkinsConnector.getTestJobResults(job.jenkinsUrl).map: testJobResults =>
                               acc :+ job.copy(latestBuild = job.latestBuild.map(_.copy(testJobResults = testJobResults)))
                           else Future.successful(acc :+ job)
      pipelineDetails <- buildDeployApiConnector
                           .getBuildJobsDetails()
                           .map(_.map(x => (x.repoName, x.buildJobs.find(_.jobType == BuildDeployApiConnector.JobType.Pipeline))))
                           .map(_.collect { case (a, Some(b)) => (a, b) })
      pipelineJobs    <- pipelineDetails.foldLeftM[Future, List[JenkinsJobsPersistence.Job]](List.empty) { case (acc, (repoName, pipelineDetail)) =>
                          jenkinsConnector
                            .getLatestBuildData(pipelineDetail.jenkinsUrl)
                            .map(latestBuild =>
                              JenkinsJobsPersistence.Job(
                                repoName    = repoName
                              , jobName     = pipelineDetail.jobName
                              , jobType     = JenkinsJobsPersistence.JobType.Pipeline
                              , repoType    = None
                              , testType    = None
                              , jenkinsUrl  = pipelineDetail.jenkinsUrl
                              , latestBuild = latestBuild
                            ) :: acc)
                         }
      _               <- jenkinsJobsPersistence.putAll(updatedJobs ++ pipelineJobs)
    yield ()
