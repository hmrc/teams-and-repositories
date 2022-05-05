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

import com.amazonaws.auth.AWSCredentialsProvider
import io.ticofab.AwsSigner
import play.api.Logging
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.teamsandrepositories.config.BuildDeployApiConfig

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HttpReads.Implicits._

@Singleton
class BuildDeployApiConnector @Inject()(
  httpClient: HttpClient,
  awsCredentialsProvider: AWSCredentialsProvider,
  config: BuildDeployApiConfig
)(
  implicit ec: ExecutionContext
) extends Logging {

  private implicit val hc = HeaderCarrier()

  def enableBranchProtection(repoName: String): Future[Unit] = {
    val url =
      url"${config.baseUrl}/v1/UpdateGithubDefaultBranchProtection"

    val payload =
      Json.toJson(BuildDeployApiConnector.Request(repoName, enable = true))

    val signedHeaders =
      awsSigner.getSignedHeaders(
        url.getPath,
        "POST",
        Map.empty,
        Map("host" -> config.host.getOrElse(url.getHost)),
        Some(Json.toBytes(payload))
      ).toSeq

    for {
      response <- httpClient.POST[JsValue, BuildDeployApiConnector.Response](url, payload, signedHeaders)
      _        <- if (response.success)
                    Future.unit
                  else
                    Future.failed(new Throwable(s"Failed to set branch protection for $repoName: ${response.message}"))
    } yield ()
  }

  private lazy val awsSigner =
    AwsSigner(
      awsCredentialsProvider,
      config.awsRegion,
      "execute-api",
      () => LocalDateTime.now()
    )
}

object BuildDeployApiConnector {

  final case class Request(
    repoName: String,
    enable: Boolean
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
