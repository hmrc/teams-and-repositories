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
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.teamsandrepositories.config.BuildDeployApiConfig
import uk.gov.hmrc.teamsandrepositories.connectors.BuildDeployApiConnector.AWSSigner

import java.net.URL
import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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

    for {
      response <- httpClient.POST[JsValue, BuildDeployApiConnector.Response](
                    url,
                    payload,
                    headers = signer.sign(url, Some(config.host), "POST", Some(payload))
                  )
      _        <- if (response.success)
                    Future.unit
                  else
                    Future.failed(new Throwable(s"Failed to set branch protection for $repoName: ${response.message}"))
    } yield ()
  }

  private lazy val signer =
    new AWSSigner(
      awsCredentialsProvider,
      config.awsRegion,
      "execute-api"
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

  final class AWSSigner(
    awsCredentials: AWSCredentialsProvider,
    awsRegion: String,
    awsService: String
  ) {

    def sign(
      url: URL,
      host: Option[String] = None,
      method: String,
      payload: Option[JsValue] = None
    ): Seq[(String, String)] = {
      AwsSigner(awsCredentials, awsRegion, awsService, () => LocalDateTime.now())
        .getSignedHeaders(
          uri = url.getPath,
          method = method,
          queryParams = toMap(url.getQuery),
          headers = Map[String, String]("host" -> host.getOrElse(url.getHost)),
          payload = payload.map(Json.toBytes)
        ).toSeq
    }

    // converts a query string into a map, note this would fall over if the same parameter was used twice
    private def toMap(query: String) = {
      Option(query)
        .map(_.split("&").map(expandQueryParam).toMap)
        .getOrElse(Map.empty)
    }

    private def expandQueryParam(param: String) = {
      val pair = param.split("=", 2)
      pair.head -> pair.last
    }
  }
}
