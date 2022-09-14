/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.mongodb.scala.result.UpdateResult
import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig
import uk.gov.hmrc.teamsandrepositories.connectors._
import uk.gov.hmrc.teamsandrepositories.models.JenkinsObject.{BuildJob, Folder, PipelineJob}
import uk.gov.hmrc.teamsandrepositories.models.{BuildData, JenkinsObject}
import uk.gov.hmrc.teamsandrepositories.persistence.BuildJobRepo

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsService @Inject()(
                                repo: BuildJobRepo,
                                jenkinsConnector: JenkinsConnector,
                                jenkinsConfig: JenkinsConfig) {

  private val logger = Logger(this.getClass)

  implicit val actorSystem: ActorSystem = ActorSystem()

  def findByService(service: String): Future[Option[BuildJob]] =
    repo.findByService(service)

  def updateBuildJobs()(implicit ec: ExecutionContext): Future[Seq[UpdateResult]] =
    for {
      res <- jenkinsConnector.findBuildJobs()
      buildJobs = res.objects flatMap extractBuildJobsFromTree
      persist <- repo.update(buildJobs)
    } yield persist

  private def extractBuildJobsFromTree(jenkinsObject: JenkinsObject): Seq[BuildJob] =
    jenkinsObject match {
      case Folder(_, _, objects) => objects flatMap extractBuildJobsFromTree
      case job: BuildJob                => Seq(job)
      case PipelineJob(_, _)        => Seq()
    }

  def triggerBuildJob(serviceName: String,
                      url: String,
                      timestamp: Instant)(implicit ec: ExecutionContext): Future[Option[BuildData]] = {
    val optionalBuild = for {
      latestBuild <- jenkinsConnector.getLastBuildTime(url)
      build = if (latestBuild.timestamp.equals(timestamp)) {
        for {
          location <- {
            logger.info(s"Triggering build for $serviceName")
            jenkinsConnector.triggerBuildJob(url)
          }
          queueUrl = s"${location.replace("http:", "https:")}"
          queue <- getQueue(queueUrl)
          build <- getBuild(queue.executable.get.url)
        } yield Some(build)
      } else {
        Future.successful(None)
      }
    } yield build
    optionalBuild.flatten
  }

  private def getQueue(queueUrl: String)(implicit ec: ExecutionContext): Future[JenkinsQueueData] = {
    Source.repeat(())
      .throttle(1, jenkinsConfig.queueThrottleDuration)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getQueueDetails(queueUrl))
      .filter(queue => queue.cancelled.nonEmpty || queue.executable.nonEmpty)
      .map(queue => {
        if (queue.cancelled.nonEmpty && queue.cancelled.get) throw new Exception("Queued Job cancelled")
        queue
      })
      .filter(queue => queue.executable.nonEmpty)
      .runWith(Sink.head)
  }

  private def getBuild(buildUrl: String)(implicit ec: ExecutionContext): Future[BuildData] = {
    Source.repeat(())
      .throttle(1, jenkinsConfig.buildThrottleDuration)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getBuild(buildUrl))
      .filter(build => build.result.nonEmpty)
      .runWith(Sink.head)
  }
}
