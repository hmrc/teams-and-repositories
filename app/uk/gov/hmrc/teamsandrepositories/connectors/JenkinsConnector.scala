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

import play.api.Logging
import play.api.http.Status
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig

import java.net.URL
import javax.inject.Inject
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class JenkinsConnector @Inject()(
  config      : JenkinsConfig,
  httpClientV2: HttpClientV2
) extends Logging:
  import HttpReads.Implicits._
  import JenkinsConnector._

  private given Reads[LatestBuild]                = LatestBuild.jenkinsReads
  private given Reads[LatestBuild.TestJobResults] = LatestBuild.TestJobResults.jenkinsReads
  private given Reads[Seq[JenkinsObject]]         = JenkinsObjects.jenkinsReads

  def triggerBuildJob(baseUrl: String)(using ExecutionContext): Future[String] =
    // Prevents Server-Side Request Forgery
    assert(baseUrl.startsWith(config.BuildJobs.baseUrl), s"$baseUrl was requested for invalid host")

    given HeaderCarrier = HeaderCarrier()
    val url = url"$baseUrl/buildWithParameters"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> config.BuildJobs.rebuilderAuthorizationHeader)
      .execute[HttpResponse]
      .flatMap: res =>
        if Status.isSuccessful(res.status) then
          res.header("Location") match
            case Some(location) => Future.successful(location.replace("http:", "https:"))
            case None           => Future.failed(sys.error(s"No location header found in response from $url"))
        else Future.failed(sys.error(s"Call to $url failed with status: ${res.status}, body: ${res.body}"))
      .recoverWithLogging(url)

  def getLatestBuildData(jobUrl: String)(using ExecutionContext): Future[Option[LatestBuild]] =
    // Prevents Server-Side Request Forgery
    assert(jobUrl.startsWith(config.BuildJobs.baseUrl), s"$jobUrl does not match expected base url: ${config.BuildJobs.baseUrl}")

    given HeaderCarrier = HeaderCarrier()
    val url = url"$jobUrl/lastBuild/api/json?tree=number,url,timestamp,result"
    logger.info(s"Requesting latest build data from: $url")

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[Option[LatestBuild]]
      .recoverWithLogging(url)

  private def extractFreeStyleProjectFromTree(jenkinsObject: JenkinsObject): Seq[JenkinsObject.FreeStyleProject] =
    jenkinsObject match
      case JenkinsObject.Folder(_, _, objects)                                     => objects.flatMap(extractFreeStyleProjectFromTree)
      case job: JenkinsObject.FreeStyleProject if job.gitHubUrl.exists(_.nonEmpty) => Seq(job)
      case _                                                                       => Seq.empty

  def findBuildJobs()(using ExecutionContext): Future[Seq[JenkinsObject.FreeStyleProject]] =
    findJobs(
      url        = url"${config.BuildJobs.baseUrl}api/json?tree=${JenkinsConnector.generateJobQuery(config.searchDepth)}"
    , authHeader = config.BuildJobs.authorizationHeader
    )

  def findPerformanceJobs()(using ExecutionContext): Future[Seq[JenkinsObject.FreeStyleProject]] =
    findJobs(
      url        = url"${config.PerformanceJobs.baseUrl}api/json?tree=${JenkinsConnector.generateJobQuery(config.searchDepth)}"
    , authHeader = config.PerformanceJobs.authorizationHeader
    )

  private def findJobs(url: java.net.URL, authHeader: String)(using ExecutionContext): Future[Seq[JenkinsObject.FreeStyleProject]] =
    given HeaderCarrier = HeaderCarrier()

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> authHeader)
      .execute[Seq[JenkinsObject]]
      .map(_.flatMap(extractFreeStyleProjectFromTree))
      .recoverWithLogging(url)

  def getQueueDetails(queueUrl: String)(using ExecutionContext): Future[JenkinsQueueData] =
    assert(queueUrl.startsWith(config.BuildJobs.baseUrl), s"$queueUrl was requested for invalid host")

    given HeaderCarrier = HeaderCarrier()
    val url = url"${queueUrl}api/json?tree=cancelled,executable[number,url]"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[JenkinsQueueData]
      .recoverWithLogging(url)

  def getBuild(buildUrl: String)(using ExecutionContext): Future[LatestBuild] =
    // Prevents Server-Side Request Forgery
    assert(buildUrl.startsWith(config.BuildJobs.baseUrl), s"$buildUrl was requested for invalid host")

    given HeaderCarrier = HeaderCarrier()
    val url = url"${buildUrl}api/json?tree=number,url,timestamp,result"

    httpClientV2
      .post(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[LatestBuild]
      .recoverWithLogging(url)

  def getBuildTestJobResults(jenkinsUrl: String)(using ExecutionContext): Future[Option[LatestBuild.TestJobResults]] =
    // Prevents Server-Side Request Forgery
    assert(jenkinsUrl.startsWith(config.BuildJobs.baseUrl), s"$jenkinsUrl was requested for invalid host")

    given HeaderCarrier = HeaderCarrier()
    val url = url"${jenkinsUrl}lastBuild/artifact/test-results.json"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> config.BuildJobs.authorizationHeader)
      .execute[Option[LatestBuild.TestJobResults]]
      .recoverWithLogging(url)

  def getPerformanceTestJobResults(jenkinsUrl: String)(using ExecutionContext): Future[Option[LatestBuild.TestJobResults]] =
    // Prevents Server-Side Request Forgery
    assert(jenkinsUrl.startsWith(config.PerformanceJobs.baseUrl), s"$jenkinsUrl was requested for invalid host")

    given HeaderCarrier = HeaderCarrier()
    val url = url"${jenkinsUrl}lastBuild/artifact/test-results.json"

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> config.PerformanceJobs.authorizationHeader)
      .execute[Option[LatestBuild.TestJobResults]]
      .recoverWithLogging(url)

  extension [T](future: Future[T])
    private def recoverWithLogging(url: URL)(using ExecutionContext): Future[T] =
      future.recoverWith:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)

object JenkinsConnector:
  def generateJobQuery(depth: Int): String =
    (0 until depth).foldLeft(""){(acc, _) =>
      s"jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]]${if acc == "" then "" else s",$acc"}]"
    }

  case class JenkinsQueueData(cancelled: Option[Boolean], executable: Option[JenkinsQueueExecutable])

  object JenkinsQueueData:
    given Reads[JenkinsQueueData] =
      ( (__ \ "cancelled" ).readNullable[Boolean]
      ~ (__ \ "executable").readNullable[JenkinsQueueExecutable]
      )(JenkinsQueueData.apply _)

  case class JenkinsQueueExecutable(number: Int, url: String)

  object JenkinsQueueExecutable:
    given Reads[JenkinsQueueExecutable] =
      ( (__ \ "number").read[Int]
      ~ (__ \ "url").read[String]
      )(JenkinsQueueExecutable.apply _)

  sealed trait JenkinsObject

  object JenkinsObject:

    case class Folder(
      name      : String,
      jenkinsUrl: String,
      jobs      : Seq[JenkinsObject]
    ) extends JenkinsObject

    case class FreeStyleProject(
      name       : String,
      jenkinsUrl : String,
      latestBuild: Option[LatestBuild],
      gitHubUrl  : Option[String]
    ) extends JenkinsObject

    case class WorkflowJob(
      name      : String,
      jenkinsUrl: String
    ) extends JenkinsObject

    private def extractGithubUrl: Reads[Option[String]] =
      Reads[Option[String]] { js =>
        val l: List[JsValue] = (__ \ "scm" \ "userRemoteConfigs" \\ "url") (js)
        l.headOption match
          case Some(v) => JsSuccess(Some(v.as[String].toLowerCase)) // github organisation can be uppercase
          case None    => JsSuccess(None)
      }

    private val readsFreeStyleProject: Reads[FreeStyleProject] =
      ( (__ \ "name"     ).read[String]
      ~ (__ \ "url"      ).read[String]
      ~ (__ \ "lastBuild").readNullable[LatestBuild](LatestBuild.jenkinsReads)
      ~ extractGithubUrl
      )(FreeStyleProject.apply _)

    private lazy val folderReads: Reads[Folder] =
      ( (__ \ "name").read[String]
      ~ (__ \ "url" ).read[String]
      ~ (__ \ "jobs").lazyRead(Reads.seq[JenkinsObject])
      )(Folder.apply _)

    private val readsWorkflowJob: Reads[WorkflowJob] =
      ( (__ \ "name").read[String]
      ~ (__ \ "url" ).read[String]
      )(WorkflowJob.apply _)

    given Reads[JenkinsObject] = json =>
      (json \ "_class").validate[String].flatMap {
        case "com.cloudbees.hudson.plugins.folder.Folder"     => folderReads.reads(json)
        case "hudson.model.FreeStyleProject"                  => readsFreeStyleProject.reads(json)
        case "org.jenkinsci.plugins.workflow.job.WorkflowJob" => readsWorkflowJob.reads(json)
        case value                                            => throw Exception(s"Unsupported Jenkins class $value")
      }

  private[connectors] object JenkinsObjects:
    given jenkinsReads: Reads[Seq[JenkinsObject]] =
      (__ \ "jobs").read(Reads.seq[JenkinsObject])

  case class LatestBuild(
    number        : Int,
    url           : String,
    timestamp     : Instant,
    result        : Option[LatestBuild.BuildResult],
    description   : Option[String],
    testJobResults: Option[LatestBuild.TestJobResults] = None
  )
  object LatestBuild:
    enum BuildResult(val asString: String):
      case Failure  extends BuildResult("FAILURE" )
      case Success  extends BuildResult("SUCCESS" )
      case Aborted  extends BuildResult("ABORTED" )
      case Unstable extends BuildResult("UNSTABLE")
      case Other    extends BuildResult("Other"   )

    object BuildResult:
      def parse(s: String): BuildResult =
        values
          .find(_.asString.equalsIgnoreCase(s)).getOrElse(Other)

      given Format[BuildResult] =
        Format.of[String].inmap(parse, _.asString)

    case class TestJobResults(
      securityAlerts         : String,
      accessibilityViolations: Option[String]
    )

    object TestJobResults:
      val apiWrites: Writes[TestJobResults] =
        ( (__ \ "securityAlerts"         ).write[String]
        ~ (__ \ "accessibilityViolations").writeNullable[String]
        )(t => Tuple.fromProductTyped(t))

      val jenkinsReads: Reads[TestJobResults] =
        ( (__ \ "securityAlerts"         ).read[String]
        ~ (__ \ "accessibilityViolations").readNullable[String]
        )(TestJobResults.apply _)

    val apiWrites: Writes[LatestBuild] =
      ( (__ \ "number"        ).write[Int]
      ~ (__ \ "url"           ).write[String]
      ~ (__ \ "timestamp"     ).write[Instant]
      ~ (__ \ "result"        ).writeNullable[BuildResult]
      ~ (__ \ "description"   ).writeNullable[String]
      ~ (__ \ "testJobResults").writeNullable[TestJobResults](TestJobResults.apiWrites)
      )(a => Tuple.fromProductTyped(a))

    val jenkinsReads: Reads[LatestBuild] =
      ( (__ \ "number"        ).read[Int]
      ~ (__ \ "url"           ).read[String]
      ~ (__ \ "timestamp"     ).read[Instant]
      ~ (__ \ "result"        ).readNullable[BuildResult]
      ~ (__ \ "description"   ).readNullable[String]
      ~ (__ \ "testJobResults").readNullable[TestJobResults](TestJobResults.jenkinsReads)
      )(apply _)
