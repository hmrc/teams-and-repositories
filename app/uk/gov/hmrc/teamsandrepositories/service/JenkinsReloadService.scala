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
import uk.gov.hmrc.teamsandrepositories.connector.{BuildDeployApiConnector, JenkinsConnector}
import uk.gov.hmrc.teamsandrepositories.connector.JenkinsConnector.LatestBuild.TestJobResults
import uk.gov.hmrc.teamsandrepositories.notification.MdtpEventHandler.JenkinsBuildEvent
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence.Job
import uk.gov.hmrc.teamsandrepositories.model.RepoType

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

  private def extractTestJobResults(description: String): Option[TestJobResults] =
    val securityAlerts      = """Security alerts: (\d+)"""     .r.findFirstMatchIn(description).map(_.group(1).toInt)
    val accessibilityIssues = """Accessibility issues: (\d+)""".r.findFirstMatchIn(description).map(_.group(1).toInt)

    if securityAlerts.isEmpty && accessibilityIssues.isEmpty then None
    else Some(TestJobResults(numAccessibilityViolations = accessibilityIssues, numSecurityAlerts = securityAlerts))

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
                                , jobType     = determineJobType(job.name, repo.repoType)
                                , testType    = repo.testType
                                , jenkinsUrl  = job.jenkinsUrl
                                , repoType    = Some(repo.repoType)
                                , latestBuild = job.latestBuild
                                ) :: acc
                              ))
                            }
      updatedJobs     <- jobs.foldLeftM[Future, List[JenkinsJobsPersistence.Job]](List.empty): (acc, job) =>
                           job.jobType match
                             case JenkinsJobsPersistence.JobType.Test =>
                               jenkinsConnector.getTestJobResults(job.jenkinsUrl).map:
                                 case Some(results) => acc :+ job.copy(latestBuild = job.latestBuild.map(_.copy(testJobResults = Some(results))))
                                 case None          => // used for old builds that publish test results in the description instead of as json
                                                       val extractedResults        = job.latestBuild.flatMap(_.description).flatMap(extractTestJobResults)
                                                       acc :+ job.copy(latestBuild = job.latestBuild.map(_.copy(testJobResults = extractedResults)))
                             case _ =>
                               Future.successful(acc :+ job)
      pipelineDetails <- buildDeployApiConnector
                           .getBuildJobsDetails()
                           .map(_.map(x => (x.repoName, x.buildJobs.find(_.jobType == BuildDeployApiConnector.JobType.Pipeline))))
                           .map(_.collect { case (a, Some(b)) => (a, b) })
      pipelineJobs    <- pipelineDetails.foldLeftM[Future, List[JenkinsJobsPersistence.Job]](List.empty) { case (acc, (repoName, pipelineDetail)) =>
                          jenkinsConnector
                            .getLatestBuildData(pipelineDetail.jenkinsUrl)
                            .map: latestBuild =>
                              JenkinsJobsPersistence.Job(
                                repoName    = repoName
                              , jobName     = pipelineDetail.jobName
                              , jobType     = JenkinsJobsPersistence.JobType.Pipeline
                              , repoType    = None
                              , testType    = None
                              , jenkinsUrl  = pipelineDetail.jenkinsUrl
                              , latestBuild = latestBuild
                              ) :: acc
                         }
      _               <- jenkinsJobsPersistence.putAll(updatedJobs ++ pipelineJobs)
    yield ()

  def updateJob(event: JenkinsBuildEvent)(using ExecutionContext): Future[Unit] =
    jenkinsJobsPersistence
      .findByJenkinsUrl(event.jobUrl)
      .flatMap:
        case Some(job) => updateExistingJob(job, event)
        case None      => updateNewJob(event)

  private def updateExistingJob(job: Job, event: JenkinsBuildEvent)(using ExecutionContext): Future[Unit] =
    for
      latestBuild <- jenkinsConnector.getLatestBuildData(event.jobUrl)
      updatedJob  <- job.jobType match
                       case JenkinsJobsPersistence.JobType.Test =>
                         jenkinsConnector.getTestJobResults(job.jenkinsUrl).map { testResults =>
                           val buildWithTestResults = latestBuild.map(build =>
                             testResults match
                               case Some(results) => build.copy(testJobResults = Some(results))
                               case None          => 
                                 // fallback to extracting from description for old builds
                                 val extractedResults = build.description.flatMap(extractTestJobResults)
                                 build.copy(testJobResults = extractedResults)
                           )
                           job.copy(latestBuild = buildWithTestResults)
                         }
                       case _ =>
                         Future.successful(job.copy(latestBuild = latestBuild))
      _           <- jenkinsJobsPersistence.putOne(updatedJob)
    yield ()

  private def updateNewJob(event: JenkinsBuildEvent)(using ExecutionContext): Future[Unit] =
    event.githubUrl match
      case RepoNameRegex(repoName) =>
        for
          repoOpt <- repositoriesPersistence.findRepo(repoName)
          _       <- repoOpt match
                       case Some(repo) =>
                         for
                           latestBuild <- jenkinsConnector.getLatestBuildData(event.jobUrl)
                           jobType     =  determineJobType(event.jobName, repo.repoType)
                           job         =  JenkinsJobsPersistence.Job(
                                            repoName    = repo.name
                                          , jobName     = event.jobName
                                          , jobType     = jobType
                                          , testType    = repo.testType
                                          , jenkinsUrl  = event.jobUrl
                                          , repoType    = Some(repo.repoType)
                                          , latestBuild = latestBuild
                                          )
                           updatedJob  <- jobType match
                                            case JenkinsJobsPersistence.JobType.Test =>
                                              jenkinsConnector.getTestJobResults(event.jobUrl).map:
                                                case Some(results) =>
                                                  job.copy(latestBuild = job.latestBuild.map(_.copy(testJobResults = Some(results))))
                                                case None          => 
                                                  val extractedResults = job.latestBuild.flatMap(_.description).flatMap(extractTestJobResults)
                                                  job.copy(latestBuild = job.latestBuild.map(_.copy(testJobResults = extractedResults)))
                                            case _ =>
                                              Future.successful(job)
                           _           <- jenkinsJobsPersistence.putOne(updatedJob)
                         yield ()
                       case None =>
                         Future.failed(sys.error("unknown github repository"))
        yield ()
      case _ =>
        // skip
        Future.unit

  private def determineJobType(jobName: String, repoType: RepoType): JenkinsJobsPersistence.JobType =
    if      jobName.endsWith("-pr-builder") then JenkinsJobsPersistence.JobType.PullRequest
    else if repoType == RepoType.Test       then JenkinsJobsPersistence.JobType.Test
    else                                         JenkinsJobsPersistence.JobType.Job
    
