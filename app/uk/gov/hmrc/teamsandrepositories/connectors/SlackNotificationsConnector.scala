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
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.teamsandrepositories.config.SlackConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SlackNotificationsConnector @Inject()(
  httpClientV2  : HttpClientV2,
  configuration : SlackConfig,
  servicesConfig: ServicesConfig,
)(implicit ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(this.getClass.getName)

  val url: String = servicesConfig.baseUrl("slack-notifications")

  private val authorizationHeaderValue = {
    val username = configuration.user
    val password = configuration.password

    s"Basic ${BaseEncoding.base64().encode(s"$username:$password".getBytes("UTF-8"))}"
  }

  def sendMessage(message: SlackNotificationRequest): Future[SlackNotificationResponse] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClientV2
      .post(url"$url/slack-notifications/notification")
      .withBody(Json.toJson(message))
      .replaceHeader("Authorization" -> authorizationHeaderValue)
      .execute[SlackNotificationResponse]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"Unable to notify ${message.channelLookup} on Slack", ex)
          Future.failed(ex)
      }
  }
}

final case class SlackNotificationError(
  code: String,
  message: String
)

object SlackNotificationError {
  implicit val format: OFormat[SlackNotificationError] =
    Json.format[SlackNotificationError]
}

final case class SlackNotificationResponse(
  successfullySentTo: Seq[String] = Nil,
  errors: List[SlackNotificationError] = Nil
) {
  def hasSentMessages: Boolean = successfullySentTo.nonEmpty
}

object SlackNotificationResponse {

  implicit val format: OFormat[SlackNotificationResponse] =
    Json.format[SlackNotificationResponse]
}

case class ChannelLookup (repositoryName: String, by: String = "github-repository")

object ChannelLookup {
  implicit val format: OFormat[ChannelLookup] = Json.format[ChannelLookup]
}

final case class Attachment(text: String, fields: Seq[Attachment.Field] = Nil)

object Attachment {
  final case class Field(
    title: String,
    value: String,
    short: Boolean
  )

  object Field {
    implicit val format: OFormat[Field] = Json.format[Field]
  }

  implicit val format: OFormat[Attachment] = Json.format[Attachment]

}

final case class MessageDetails(
  text: String,
  username: String,
  iconEmoji: String,
  attachments: Seq[Attachment]
)

object MessageDetails {
  implicit val writes: OWrites[MessageDetails] = Json.writes[MessageDetails]
}

final case class SlackNotificationRequest(
  channelLookup: ChannelLookup,
  messageDetails: MessageDetails
)

object SlackNotificationRequest {
  implicit val writes: OWrites[SlackNotificationRequest] = Json.writes[SlackNotificationRequest]
}