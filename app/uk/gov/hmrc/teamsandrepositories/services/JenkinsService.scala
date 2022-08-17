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

import org.mongodb.scala.result.UpdateResult
import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
import uk.gov.hmrc.teamsandrepositories.models.{BuildJob, BuildJobBuildData}
import uk.gov.hmrc.teamsandrepositories.persistence.BuildJobRepo

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class JenkinsService @Inject()(repo: BuildJobRepo, jenkinsConnector: JenkinsConnector) {

  private val logger = Logger(this.getClass)
  def findByService(service: String): Future[Option[BuildJob]] =
    repo.findByService(service)

  def updateBuildJobs()(implicit ec: ExecutionContext): Future[Seq[UpdateResult]] =
    for {
      res <- jenkinsConnector.findBuildJobRoot()
      buildJobs = res.map(build => {
        val buildData = build.builds match {
          case Some(value) if value.nonEmpty =>
            val data = value.maxBy(_.timestamp)
            Some(BuildJobBuildData(data.number, data.url, data.timestamp, data.result))
          case _ => None
        }
        BuildJob(build.displayName, build.url, buildData)
      })
      persist <- repo.update(buildJobs)
    } yield persist

  def triggerBuildJob(url: String,
                      timestamp: Instant)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      latestBuild <- jenkinsConnector.getLastBuildTime(url)
      _ <- if (latestBuild.timestamp.equals(timestamp))
          jenkinsConnector.triggerBuildJob(url)
        else {
        logger.info("Job was rebuilt since last Jenkins query")
        Future.successful[Unit](())
      }
    } yield ()
  }
}
