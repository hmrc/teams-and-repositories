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

package uk.gov.hmrc.teamsandrepositories.connectors

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig
import uk.gov.hmrc.teamsandrepositories.models.{BuildData, BuildJob}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class JenkinsConnector @Inject()(
  config      : JenkinsConfig,
  httpClientV2: HttpClientV2
) {
  import HttpReads.Implicits._
  import JenkinsConnector._

  private val logger = Logger(this.getClass)

  private implicit val br: Reads[BuildData]          = BuildData.jenkinsReads
  private implicit val jo: Reads[Seq[JenkinsObject]] = JenkinsObjects.jenkinsReads

  def triggerBuildJob(baseUrl: String)(implicit ec: ExecutionContext): Future[String] = {
    // Prevents Server-Side Request Forgery
    assert(baseUrl.startsWith(config.BuildJobs.baseUrl), s"$baseUrl was requested for invalid host")
    implicit val locationRead: HttpReads[String] = HttpReads[HttpResponse].map(_.header("Location").get)

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"$baseUrl/buildWithParameters"
    httpClientV2
      .post(url)
      .setHeader("Authorization" -> config.BuildJobs.rebuilderAuthorizationHeader)
      .execute[String]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error (s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  def getLatestBuildData(baseUrl: String)(implicit ec: ExecutionContext): Future[BuildData] = {
    // Prevents Server-Side Request Forgery
    assert(baseUrl.startsWith(config.BuildJobs.baseUrl), s"$baseUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"$baseUrl/lastBuild/api/json?tree=number,url,timestamp,result"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[BuildData]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  private def toBuildJob(job: JenkinsObject.StandardJob): BuildJob =
    BuildJob(
      name        = job.name,
      jobType     = None,
      jenkinsUrl  = job.jenkinsUrl,
      latestBuild = job.latestBuild,
      gitHubUrl   = job.gitHubUrl
    )

  private def extractStandardJobsFromTree(jenkinsObject: JenkinsObject): Seq[BuildJob] =
    jenkinsObject match {
      case JenkinsObject.Folder(_, _, objects) => objects.flatMap(extractStandardJobsFromTree)
      case job: JenkinsObject.StandardJob      => Seq(toBuildJob(job))
      case JenkinsObject.PipelineJob(_, _)     => Seq()
    }

  def findPerformanceJobs()(implicit  ec: ExecutionContext): Future[Seq[BuildJob]] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${config.PerformanceJobs.baseUrl}api/json?tree=${JenkinsConnector.generateJobQuery(config.searchDepth)}"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> config.PerformanceJobs.authorizationHeader)
      .execute[Seq[JenkinsObject]]
      .map(_.flatMap(extractStandardJobsFromTree))
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  def getQueueDetails(queueUrl: String)(implicit ec: ExecutionContext): Future[JenkinsQueueData] = {
    assert(queueUrl.startsWith(config.BuildJobs.baseUrl), s"$queueUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${queueUrl}api/json?tree=cancelled,executable[number,url]"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[JenkinsQueueData]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  def getBuild(buildUrl: String)(implicit ec: ExecutionContext): Future[BuildData] = {
    // Prevents Server-Side Request Forgery
    assert(buildUrl.startsWith(config.BuildJobs.baseUrl), s"$buildUrl was requested for invalid host")

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val url = url"${buildUrl}api/json?tree=number,url,timestamp,result"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[BuildData]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }
}

object JenkinsConnector {
  def generateJobQuery(depth: Int): String =
    (0 until depth).foldLeft(""){(acc, _) =>
      s"jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]]${if (acc == "") "" else s",$acc"}]"
    }

  case class JenkinsQueueData(cancelled: Option[Boolean], executable: Option[JenkinsQueueExecutable])

  object JenkinsQueueData {
    implicit val queueDataReader: Reads[JenkinsQueueData] =
      ( (__ \ "cancelled" ).readNullable[Boolean]
      ~ (__ \ "executable").readNullable[JenkinsQueueExecutable]
      )(JenkinsQueueData.apply _)
  }

  case class JenkinsQueueExecutable(number: Int, url: String)

  object JenkinsQueueExecutable {
    implicit val executableReader: Reads[JenkinsQueueExecutable] =
      ( (__ \ "number").read[Int]
      ~ (__ \ "url").read[String]
      )(JenkinsQueueExecutable.apply _)
  }

  case class JenkinsJobs(jobs: Seq[JenkinsObject.StandardJob])
  object JenkinsJobs {
    implicit val reads: Reads[JenkinsJobs] = {
      implicit val x: Reads[JenkinsObject.StandardJob] = JenkinsObject.StandardJob.jenkinsReads
      Json.reads[JenkinsJobs]
    }
  }

  sealed trait JenkinsObject

  object JenkinsObject {

    case class Folder(
      name      : String,
      jenkinsUrl: String,
      jobs      : Seq[JenkinsObject]
    ) extends JenkinsObject

    case class StandardJob(
      name       : String,
      jenkinsUrl : String,
      latestBuild: Option[BuildData],
      gitHubUrl  : Option[String]
    ) extends JenkinsObject

    case class PipelineJob(
      name      : String,
      jenkinsUrl: String
    ) extends JenkinsObject

    object StandardJob {

      val mongoFormat: Format[StandardJob] =
        ( (__ \ "name"       ).format[String]
          ~ (__ \ "jenkinsURL" ).format[String]
          ~ (__ \ "latestBuild").formatNullable(BuildData.mongoFormat)
          ~ (__ \ "gitHubUrl"  ).formatNullable[String]
          )(apply, unlift(unapply))

      val apiWrites: Writes[StandardJob] =
        ( (__ \ "name"       ).write[String]
          ~ (__ \ "jenkinsURL" ).write[String]
          ~ (__ \ "latestBuild").writeNullable(BuildData.apiWrites)
          ~ (__ \ "gitHubUrl"  ).writeNullable[String]
          )(unlift(unapply))

      private def extractGithubUrl = Reads[Option[String]] { js =>
        val l: List[JsValue] = (__ \ "scm" \ "userRemoteConfigs" \\ "url") (js)
        l.headOption match {
          case Some(v) => JsSuccess(Some(v.as[String].toLowerCase)) // github organisation can be uppercase
          case None    => JsSuccess(None)
        }
      }

      val jenkinsReads: Reads[StandardJob] =
        ( (__ \ "name"     ).read[String]
          ~ (__ \ "url"      ).read[String]
          ~ (__ \ "lastBuild").readNullable[BuildData](BuildData.jenkinsReads)
          ~ extractGithubUrl
          )(StandardJob.apply _)
    }

    private lazy val folderReads: Reads[Folder] =
      ( (__ \ "name").read[String]
        ~ (__ \ "url" ).read[String]
        ~ (__ \ "jobs").lazyRead(Reads.seq[JenkinsObject](jenkinsObjectReads))
        )(Folder.apply _)

    private val pipelineReads: Reads[PipelineJob] =
      ( (__ \ "name").read[String]
        ~ (__ \ "url" ).read[String]
        )(PipelineJob.apply _)

    implicit val jenkinsObjectReads: Reads[JenkinsObject] = json =>
      (json \ "_class").validate[String].flatMap {
        case "com.cloudbees.hudson.plugins.folder.Folder"     => folderReads.reads(json)
        case "hudson.model.FreeStyleProject"                  => StandardJob.jenkinsReads.reads(json)
        case "org.jenkinsci.plugins.workflow.job.WorkflowJob" => pipelineReads.reads(json)
        case value                                            => throw new Exception(s"Unsupported Jenkins class $value")
      }
  }

  object JenkinsObjects {
    implicit val jenkinsReads: Reads[Seq[JenkinsObject]] =
      (__ \ "jobs").read(Reads.seq[JenkinsObject])
  }
}
