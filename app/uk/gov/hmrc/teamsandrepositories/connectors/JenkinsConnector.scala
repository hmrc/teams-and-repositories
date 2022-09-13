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
import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig
import uk.gov.hmrc.teamsandrepositories.models.BuildResult

import java.time.Instant
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class JenkinsConnector @Inject()(
  config      : JenkinsConfig,
  httpClientV2: HttpClientV2
) {

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

  def findBuildJobs()(implicit  ec: ExecutionContext): Future[JenkinsBuildJobsWrapper] = {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${config.baseUrl}api/json?tree=${JenkinsConnector.generateJobQuery(config.searchDepth)}"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[JenkinsBuildJobsWrapper]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

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

  def generateJobQuery(depth: Int): String = {
    @tailrec
    def go(depth: Int, acc: String): String = {
      if (depth < 1)
        acc.replace("],]", "]]")
      else
        go(depth - 1, s"jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],$acc]")
    }
    go(depth, "")
  }
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

sealed trait JenkinsObject

case class JenkinsFolder(name: String, url: String, objects: Seq[JenkinsObject]) extends JenkinsObject
case class JenkinsProject(name: String, url: String, lastBuild: Option[JenkinsBuildData]) extends JenkinsObject

case class JenkinsPipeline(name: String, url: String) extends JenkinsObject

object JenkinsObject {

  private implicit val cfg: Aux[Json.MacroOptions] = JsonConfiguration(
    discriminator = "_class",

    typeNaming = JsonNaming {
      case "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsFolder" => "com.cloudbees.hudson.plugins.folder.Folder"
      case "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsProject" => "hudson.model.FreeStyleProject"
      case "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsPipeline" => "org.jenkinsci.plugins.workflow.job.WorkflowJob"
    }
  )

  private implicit val folderReads: Reads[JenkinsFolder] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
      ~ (__ \ "jobs").lazyRead(Reads.seq[JenkinsObject])
  ) (JenkinsFolder)
  private implicit val projectReads: Reads[JenkinsProject] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
      ~ (__ \ "lastBuild").readNullable[JenkinsBuildData]
    ) (JenkinsProject)
  private implicit val pipelineReads: Reads[JenkinsPipeline] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
    ) (JenkinsPipeline)

  implicit val reads: Reads[JenkinsObject] = Json.reads[JenkinsObject]
}

case class JenkinsBuildJobsWrapper(jobs: Seq[JenkinsObject])

object JenkinsBuildJobsWrapper {
  implicit val reads: Reads[JenkinsBuildJobsWrapper] = Json.reads[JenkinsBuildJobsWrapper]
}
