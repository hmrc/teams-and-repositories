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
import uk.gov.hmrc.teamsandrepositories.connectors.{JenkinsBuildData, JenkinsConnector, JenkinsQueueData}
import uk.gov.hmrc.teamsandrepositories.models.{BuildJob, BuildJobBuildData}
import uk.gov.hmrc.teamsandrepositories.persistence.BuildJobRepo

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

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
      res <- jenkinsConnector.findBuildJobRoot()
      buildJobs = res.map(build => {

        val buildData = build
          .builds
          .reduceOption( (a, b) => if(a.timestamp.compareTo(b.timestamp) > 0) a else b)
          .map(data => BuildJobBuildData(data.number, data.url, data.timestamp, data.result))

        BuildJob(build.displayName, build.url, buildData)
      })
      persist <- repo.update(buildJobs)
    } yield persist

  def triggerBuildJob(serviceName: String, url: String,
                      timestamp: Instant)(implicit ec: ExecutionContext): Future[JenkinsBuildData] = {
    for {
      latestBuild <- jenkinsConnector.getLastBuildTime(url)
      location <- {
        logger.info(s"Triggering build for $serviceName")
        jenkinsConnector.triggerBuildJob(url)
      } if latestBuild.timestamp.equals(timestamp)
      queueUrl = s"${location.replace("http:", "https:")}api/json"
      queue <- getQueue(queueUrl)
      build <- getBuild(queue.executable.get.url)
    } yield build
  }

  private def getQueue(queueUrl: String)(implicit ec: ExecutionContext): Future[JenkinsQueueData] = {
    Source.repeat(42)
        .throttle(1, jenkinsConfig.queueThrottleInSeconds seconds)
        .mapAsync(parallelism = 1)(_ => jenkinsConnector.getQueueDetails(queueUrl))
        .filter(queue => queue.cancelled.nonEmpty)
      .map(queue => {
        if (queue.cancelled.get) throw new Exception("Queued Job cancelled")
        queue
      })
        .filter(queue => queue.executable.nonEmpty)
        .runWith(Sink.head)
  }

  private def getBuild(buildUrl: String)(implicit ec: ExecutionContext): Future[JenkinsBuildData] = {
    Source.repeat(42)
      .throttle(1, jenkinsConfig.buildThrottleInMinutes minute)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getBuild(buildUrl))
      .filter(build => build.result.nonEmpty)
      .runWith(Sink.head)
  }

}
