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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.BuildDeployApiConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class BuildDeployApiConnector @Inject()(
  httpClientV2          : HttpClientV2,
  config                : BuildDeployApiConfig
)(implicit
  ec: ExecutionContext
) extends Logging {

  import BuildDeployApiConnector._

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  def getBuildJobsDetails(): Future[Seq[BuildDeployApiConnector.Detail]] = {
    implicit val dr: Reads[Seq[Detail]] =
      Reads.at(__ \ "details")(Reads.seq(Detail.reads))

    val url =
      url"${config.baseUrl}/get-build-jobs"

    httpClientV2.post(url)
      .execute[Seq[Detail]]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  def enableBranchProtection(repoName: String): Future[Unit] = {

    val url =
      url"${config.baseUrl}/set-branch-protection"

    val payload =
      Json.toJson(BuildDeployApiConnector.Request(repoName, enable = true))

    for {
      response <- httpClientV2
                    .post(url)
                    .withBody(payload)
                    .execute[BuildDeployApiConnector.Response]
      _        <- if (response.success)
                    Future.unit
                  else
                    Future.failed(new Throwable(s"Failed to set branch protection for $repoName: ${response.message}"))
    } yield ()
  }
}

object BuildDeployApiConnector {

  case class Detail(
   repoName : String,
   buildJobs: List[BuildJob]
  )

  case class BuildJob(
    jobName    : String,
    jenkinsUrl : String,
    jobType    : JobType,
  )

  object Detail {
    val reads: Reads[Detail] = {
      implicit val readsBuildJob: Reads[BuildJob] =
        ( (__ \ "name").read[String].map(_.split("/").last)
        ~ (__ \ "url" ).read[String]
        ~ (__ \ "type").read[JobType]
        )(BuildJob.apply _)

      ( (__ \ "repository_name").read[String]
      ~ (__ \ "build_jobs"     ).read[List[BuildJob]]
      )(Detail.apply _)
    }
  }

  sealed trait JobType { def asString: String }

  object JobType {
    case object Job         extends JobType { override val asString = "job"         }
    case object Pipeline    extends JobType { override val asString = "pipeline"    }

    val values: List[JobType] = List(Job, Pipeline)

    def parse(s: String): JobType =
      values
        .find(_.asString.equalsIgnoreCase(s)).getOrElse {
          Job
      }

    implicit val format: Format[JobType] =
      Format.of[String].inmap(parse, _.asString)
  }


  final case class Request(
    repoName: String,
    enable  : Boolean
  )

  object Request {

    implicit val writes: Writes[Request] =
      ( (__ \ "repository_names"          ).write[String]
      ~ (__ \ "set_branch_protection_rule").write[Boolean]
      )(unlift(Request.unapply))
  }

  final case class Response(
    success: Boolean,
    message: String
  )

  object Response {

    implicit val reads: Reads[Response] =
      Reads.list {
        ( (__ \ "success").read[Boolean]
        ~ (__ \ "message").read[String]
        ) (Response.apply _)
      }.collect(JsonValidationError("A single-element array of responses was expected but not found")) {
        case x :: Nil => x
      }
  }
}
