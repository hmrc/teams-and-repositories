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

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import io.ticofab.AwsSigner
import play.api.Logging
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http._
import uk.gov.hmrc.teamsandrepositories.config.BranchProtectionApiConfig

@Singleton
class BranchProtectionApiConnector @Inject()(
  httpClient: HttpClient,
  config: BranchProtectionApiConfig
)(
  implicit ec: ExecutionContext
) extends Logging {

  private implicit val hc = HeaderCarrier()

  private val awsSigner =
    AwsSigner(
      DefaultAWSCredentialsProviderChain.getInstance(),
      config.awsRegion,
      "execute-api",
      () => LocalDateTime.now()
    )

  def setBranchProtection(repoName: String): Future[Unit] = {

    val payload =
      BranchProtectionPayload(List(repoName), enable = true)

    val signedHeaders =
      awsSigner.getSignedHeaders(
        config.url.getPath,
        "POST",
        Map.empty,
        Map("host" -> config.host.getOrElse(config.url.getHost)),
        Some(Json.toBytes(Json.toJson(payload)))
      ).toSeq

    httpClient
      .POST[BranchProtectionPayload, HttpResponse](config.url, payload, signedHeaders)
      .map { r => logger.info(s"Setting BP for $repoName: Status code = ${r.status}; Body = ${r.body}") }
  }

  private final case class BranchProtectionPayload(
    repositoryNames: List[String],
    enable: Boolean
  )

  private implicit lazy val branchProtectionPayloadWrites: Writes[BranchProtectionPayload] =
    ( (__ \ "repository_names"          ).write[String].contramap[List[String]](_.mkString(","))
    ~ (__ \ "set_branch_protection_rule").write[Boolean]
    )(unlift(BranchProtectionPayload.unapply))
}