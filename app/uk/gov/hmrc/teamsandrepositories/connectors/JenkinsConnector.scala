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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
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

   def findBuildJobRoot(): Future[Seq[JenkinsJob]] =
     for {
       root <- findBuildJobs(config.baseUrl)
       res  <- JenkinsConnector.parse(root, findBuildJobs)
     } yield res
}

object JenkinsConnector {
  private def isFolder(job: JenkinsJob): Boolean =
    job._class == "com.cloudbees.hudson.plugins.folder.Folder"

  private def isProject(job: JenkinsJob): Boolean =
    job._class == "hudson.model.FreeStyleProject"

  import cats.implicits._

  def parse(root: JenkinsRoot, findBuildJobsFunction: String => Future[JenkinsRoot]): Future[Seq[JenkinsJob]] = {
    root.jobs.toList.traverse {
      case job if isFolder(job)  => findBuildJobsFunction(job.url).flatMap(parse(_, findBuildJobsFunction))
      case job if isProject(job) => Future(Seq(job))
      case _                     => Future(Seq.empty)
    }.map(_.flatten)
  }
}

case class JenkinsRoot(_class: String, jobs: Seq[JenkinsJob])

case class JenkinsJob (_class: String, displayName: String, url: String)

object JenkinsApiReads {
  implicit val jenkinsRootReader: Reads[JenkinsRoot] =
    ( (__ \ "_class").read[String]
    ~ (__ \ "jobs"  ).lazyRead(Reads.seq[JenkinsJob])
    )(JenkinsRoot.apply _)

  implicit val jenkinsJobReader: Reads[JenkinsJob] =
    ( (__ \ "_class").read[String]
    ~ (__ \ "name"  ).read[String]
    ~ (__ \ "url"   ).read[String]
    )(JenkinsJob.apply _)
}
