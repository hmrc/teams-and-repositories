package uk.gov.hmrc.teamsandrepositories.connectors

import java.util.concurrent.Executors

import com.google.common.io.BaseEncoding
import javax.inject.Inject
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

class JenkinsConnector @Inject()(config: JenkinsConfig, http: HttpClient) {

  import JenkinsApiReads._

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))

  private val buildsUrl: String = "api/json?tree=jobs[name,url]"

  private def findBuildJobs(url: String): Future[JenkinsRoot] = {
    // Prevents Server-Side Request Forgery
    assert(url.startsWith(config.baseUrl), s"$url was requested for invalid host")

    val authorizationHeader: Option[Authorization] = {
      val authorizationValue =
        s"Basic ${BaseEncoding.base64().encode(s"${config.username}:${config.password}".getBytes("UTF-8"))}"
      Some(Authorization(authorizationValue))
    }

    implicit val hc: HeaderCarrier = HeaderCarrier(authorizationHeader)

    http
      .GET[JenkinsRoot](s"$url$buildsUrl")
      .recoverWith {
        case NonFatal(ex) =>
          Logger.error(s"An error occurred when connecting to $url$buildsUrl: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

   def findBuildJobRoot(): Future[Seq[JenkinsJob]] = {
    findBuildJobs(config.baseUrl).map(root => JenkinsConnector.parse(root, findBuildJobs))
  }
}

object JenkinsConnector {
  private def isFolder(job: JenkinsJob): Boolean = {
    job._class == "com.cloudbees.hudson.plugins.folder.Folder"
  }

  private def isFolderOrProject(job: JenkinsJob): Boolean = {
    job._class == "com.cloudbees.hudson.plugins.folder.Folder" || job._class == "hudson.model.FreeStyleProject"
  }

  def parse(root: JenkinsRoot, findBuildJobsFunction: String => Future[JenkinsRoot]): Seq[JenkinsJob] = {

    var jobs = root.jobs.filter(isFolderOrProject)

    while (jobs.exists(isFolder)) {
      val folder = jobs.find(isFolder)

      folder.foreach( f => {
        val res = Await.result(findBuildJobsFunction(f.url), Duration(20, "seconds"))
        jobs = res.jobs.filter(isFolderOrProject) ++ jobs.filterNot(_ == f)
      })
    }

    jobs
  }
}

case class JenkinsRoot (_class: String, jobs: Seq[JenkinsJob])

case class JenkinsJob(_class: String, displayName: String, url: String)

object JenkinsApiReads {
  implicit val jenkinsRootReader: Reads[JenkinsRoot] = (
    (JsPath \ "_class").read[String] and
      (JsPath \ "jobs").lazyRead(Reads.seq[JenkinsJob])
  )(JenkinsRoot.apply _)

  implicit val jenkinsJobReader: Reads[JenkinsJob] = (
    (JsPath \ "_class").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "url").read[String]
    )(JenkinsJob.apply _)
}
