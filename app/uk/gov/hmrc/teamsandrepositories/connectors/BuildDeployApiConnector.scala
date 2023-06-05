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

import play.api.{Logger, Logging}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.BuildDeployApiConfig
import uk.gov.hmrc.teamsandrepositories.connectors.BuildDeployApiConnector.{BuildJob, Detail, Result}
import uk.gov.hmrc.teamsandrepositories.connectors.signer.AwsSigner

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class BuildDeployApiConnector @Inject()(
  httpClientV2          : HttpClientV2,
  awsCredentialsProvider: AwsCredentialsProvider,
  config                : BuildDeployApiConfig
)(implicit
  ec: ExecutionContext
) extends Logging {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  def getJobs(): Future[Result] = {
    implicit val detailReads: Reads[Result] = Result.reads

    val queryParams = Map.empty[String, String]

    val url =
      url"${config.baseUrl}/v1/GetBuildJobs?$queryParams"

    val headers = AwsSigner(awsCredentialsProvider, config.awsRegion, "execute-api", () => LocalDateTime.now())
      .getSignedHeaders(
        uri         = url.getPath,
        method      = "POST",
        queryParams = queryParams,
        headers     = Map[String, String]("host" -> config.host),
        payload     = None
      )

      httpClientV2.post(url)
        .setHeader(headers.toSeq: _*)
        .execute[Result]
        .recoverWith {
          case NonFatal(ex) =>
            logger.error (s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
            Future.failed(ex)
        }
  }

  def enableBranchProtection(repoName: String): Future[Unit] = {
    val queryParams = Map.empty[String, String]

    val url =
      url"${config.baseUrl}/v1/UpdateGithubDefaultBranchProtection?$queryParams"

    val payload =
      Json.toJson(BuildDeployApiConnector.Request(repoName, enable = true))

    val headers = AwsSigner(awsCredentialsProvider, config.awsRegion, "execute-api", () => LocalDateTime.now())
      .getSignedHeaders(
        uri         = url.getPath,
        method      = "POST",
        queryParams = queryParams,
        headers     = Map[String, String]("host" -> config.host),
        payload     = Some(Json.toBytes(payload))
      )

    for {
      response <- httpClientV2
                    .post(url)
                    .withBody(payload)
                    .setHeader(headers.toSeq: _*)
                    .execute[BuildDeployApiConnector.Response]
      _        <- if (response.success)
                    Future.unit
                  else
                    Future.failed(new Throwable(s"Failed to set branch protection for $repoName: ${response.message}"))
    } yield ()
  }
}

object BuildDeployApiConnector {

  case class BuildJob(
    name   : String,
    url    : String,
    jobType: String
  )

  object BuildJob {
    val reads: Reads[BuildJob] =
      ( (__ \ "name").read[String]
      ~ (__ \ "url" ).read[String]
      ~ (__ \ "type").read[String]
      )(BuildJob.apply _)
  }

  case class Detail(
   repoName : String,
   buildJobs: List[BuildJob]
  )

  object Detail {
    val reads: Reads[Detail] = {
      implicit val buildJobReads: Reads[BuildJob] = BuildJob.reads
      ( (__ \ "repository_name").read[String]
      ~ (__ \ "build_jobs").read[List[BuildJob]]
      )(Detail.apply _)
    }
  }

  case class Result(
    details: List[Detail]
  )

  object Result {
    val reads: Reads[Result] = {
      implicit val detailReads: Reads[Detail] = Detail.reads
      (__ \ "details").read[List[Detail]].map(Result(_))
    }
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
