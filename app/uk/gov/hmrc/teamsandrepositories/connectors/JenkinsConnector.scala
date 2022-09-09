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

package uk.gov.hmrc.teamsandrepositories.connectors

import com.google.common.io.BaseEncoding

import javax.inject.Inject
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig
import uk.gov.hmrc.teamsandrepositories.models.BuildResult

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class JenkinsConnector @Inject()(
  config      : JenkinsConfig,
  httpClientV2: HttpClientV2
) {

  import JenkinsApiReads._
  import HttpReads.Implicits._

  private val logger = Logger(this.getClass)

  private val authorizationHeader =
    s"Basic ${BaseEncoding.base64().encode(s"${config.username}:${config.token}".getBytes("UTF-8"))}"

  def triggerBuildJob(baseUrl: String)(implicit ec: ExecutionContext): Future[String] = {
    // Prevents Server-Side Request Forgery
    assert(baseUrl.startsWith(config.baseUrl), s"$baseUrl was requested for invalid host")
    implicit val locationRead: HttpReads[String] = HttpReads[HttpResponse].map(_.header("Location").get)

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"$baseUrl/buildWithParameters"
    for {
      response <- httpClientV2
        .post(url)
        .setHeader("Authorization" -> authorizationHeader)
        .execute[String]
        .recoverWith {
        case NonFatal (ex) =>
        logger.error (s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
      Future.failed (ex)
      }
    } yield response
  }

  def getLastBuildTime(baseUrl: String)(implicit  ec: ExecutionContext): Future[JenkinsBuildData] = {
    // Prevents Server-Side Request Forgery
    assert(baseUrl.startsWith(config.baseUrl), s"$baseUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"$baseUrl/lastBuild/api/json?tree=number,url,timestamp,result"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[JenkinsBuildData]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  private def findBuildJobs(baseUrl: String)(implicit ec: ExecutionContext): Future[Option[JenkinsRoot]] = {
    // Prevents Server-Side Request Forgery
    assert(baseUrl.startsWith(config.baseUrl), s"$baseUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${baseUrl}api/json?tree=jobs[name,url,builds[number,url,timestamp,result]]"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[Option[JenkinsRoot]]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.successful(None)
      }
  }

   def findBuildJobRoot()(implicit ec: ExecutionContext): Future[Seq[JenkinsJob]] =
     for {
       root <- findBuildJobs(config.baseUrl)
       res  <- JenkinsConnector.parse(root, findBuildJobs)
     } yield res

  def getQueueDetails(queueUrl: String)(implicit ec: ExecutionContext): Future[JenkinsQueueData] = {

    assert(queueUrl.startsWith(config.baseUrl), s"$queueUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${queueUrl}api/json?tree=cancelled,executable[number,url]"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[JenkinsQueueData]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  def getBuild(buildUrl: String)(implicit ec: ExecutionContext): Future[JenkinsBuildData] = {
    // Prevents Server-Side Request Forgery
    assert(buildUrl.startsWith(config.baseUrl), s"$buildUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${buildUrl}api/json?tree=number,url,timestamp,result"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[JenkinsBuildData]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }
}

object JenkinsConnector {
  private def isFolder(job: JenkinsJob): Boolean =
    job._class == "com.cloudbees.hudson.plugins.folder.Folder"

  private def isProject(job: JenkinsJob): Boolean =
    job._class == "hudson.model.FreeStyleProject"

  import cats.implicits._

  def parse(root: Option[JenkinsRoot], findBuildJobsFunction: String => Future[Option[JenkinsRoot]])(implicit ec: ExecutionContext): Future[Seq[JenkinsJob]] = {
    root match {
      case None => Future(Seq.empty)
      case Some(value) => value.jobs.toList.traverse {
        case job if isFolder (job) => findBuildJobsFunction (job.url).flatMap (parse (_, findBuildJobsFunction) )
        case job if isProject (job) => Future (Seq (job) )
        case _ => Future (Seq.empty)
      }.map (_.flatten)
    }

  }
}

case class JenkinsRoot(_class: String, jobs: Seq[JenkinsJob])

case class JenkinsJob (_class: String, displayName: String, url: String, builds: Seq[JenkinsBuildData])

object JenkinsApiReads {
  implicit val jenkinsRootReader: Reads[JenkinsRoot] =
    ( (__ \ "_class").read[String]
    ~ (__ \ "jobs"  ).lazyRead(Reads.seq[JenkinsJob])
    )(JenkinsRoot.apply _)

  implicit val jenkinsJobReader: Reads[JenkinsJob] =
    ( (__ \ "_class").read[String]
    ~ (__ \ "name"  ).read[String]
    ~ (__ \ "url"   ).read[String]
    ~ (__ \ "builds").readWithDefault[Seq[JenkinsBuildData]](Seq.empty)
    )(JenkinsJob.apply _)
}
case class JenkinsBuildData(
                      number: Int,
                      url: String,
                      timestamp: Instant,
                      result: Option[BuildResult]
                    )
object JenkinsBuildData {

  implicit val buildDataReader: Reads[JenkinsBuildData] =
    ((__ \ "number").read[Int]
      ~ (__ \ "url").read[String]
      ~ (__ \ "timestamp").read[Instant]
      ~ (__ \ "result").readNullable[BuildResult]
      ) (JenkinsBuildData.apply _)
}

case class JenkinsQueueData(cancelled: Option[Boolean], executable: Option[JenkinsQueueExecutable])

object JenkinsQueueData {
  implicit val queueDataReader: Reads[JenkinsQueueData] =
    ((__ \ "cancelled").readNullable[Boolean]
      ~ (__ \ "executable").readNullable[JenkinsQueueExecutable]
      ) (JenkinsQueueData.apply _)
}

case class JenkinsQueueExecutable(number: Int, url: String)

object JenkinsQueueExecutable {
  implicit val executableReader: Reads[JenkinsQueueExecutable] =
    ((__ \ "number").read[Int]
      ~ (__ \ "url").read[String]
      ) (JenkinsQueueExecutable.apply _)
}
