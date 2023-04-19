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

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig
import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
import uk.gov.hmrc.teamsandrepositories.models.{BuildData, JenkinsObject}
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsLinksPersistence

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsService @Inject()(
  jenkinsConnector       : JenkinsConnector,
  jenkinsLinksPersistence: JenkinsLinksPersistence,
  jenkinsConfig          : JenkinsConfig
) {
  private val logger = Logger(this.getClass)

  private implicit val actorSystem: ActorSystem = ActorSystem()

  def findByJobName(name: String): Future[Option[JenkinsObject.StandardJob]] =
    jenkinsLinksPersistence.findByJobName(name)

  def findAllByRepo(service: String)(implicit ec: ExecutionContext): Future[Seq[JenkinsObject.StandardJob]] =
    jenkinsLinksPersistence.findAllByRepo(service)

  def updateBuildAndPerformanceJobs()(implicit ec: ExecutionContext): Future[Unit] =
    for {
      bJobs <- jenkinsConnector.findBuildJobs()
      pJobs <- jenkinsConnector.findPerformanceJobs()
      jobs  =  (bJobs ++ pJobs).flatMap(extractStandardJobsFromTree)
      _     <- jenkinsLinksPersistence.putAll(jobs)
    } yield ()

  private def extractStandardJobsFromTree(jenkinsObject: JenkinsObject): Seq[JenkinsObject.StandardJob] =
    jenkinsObject match {
      case JenkinsObject.Folder(_, _, objects) => objects.flatMap(extractStandardJobsFromTree)
      case job: JenkinsObject.StandardJob      => Seq(job)
      case JenkinsObject.PipelineJob(_, _)     => Seq()
    }

  def triggerBuildJob(
    serviceName: String,
    url        : String,
    timestamp  : Instant
  )(implicit
    ec         : ExecutionContext
  ): Future[Option[BuildData]] =
    (for {
      latestBuild <- jenkinsConnector.getLastBuildTime(url)
      build = if (latestBuild.timestamp.equals(timestamp)) {
        for {
          _        <- Future.successful(logger.info(s"Triggering build for $serviceName"))
          location <- jenkinsConnector.triggerBuildJob(url)
          queueUrl =  s"${location.replace("http:", "https:")}"
          queue    <- getQueue(queueUrl)
          build    <- getBuild(queue.executable.get.url)
        } yield Some(build)
      } else {
        Future.successful(None)
      }
     } yield build
    ).flatten

  private def getQueue(queueUrl: String)(implicit ec: ExecutionContext): Future[JenkinsConnector.JenkinsQueueData] =
    Source.repeat(())
      .throttle(1, jenkinsConfig.queueThrottleDuration)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getQueueDetails(queueUrl))
      .filter(queue => queue.cancelled.nonEmpty || queue.executable.nonEmpty)
      .map { queue =>
        if (queue.cancelled.nonEmpty && queue.cancelled.get)
          throw new Exception("Queued Job cancelled")
        queue
      }
      .filter(_.executable.nonEmpty)
      .runWith(Sink.head)

  private def getBuild(buildUrl: String)(implicit ec: ExecutionContext): Future[BuildData] =
    Source.repeat(())
      .throttle(1, jenkinsConfig.buildThrottleDuration)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getBuild(buildUrl))
      .filter(_.result.nonEmpty)
      .runWith(Sink.head)
}
