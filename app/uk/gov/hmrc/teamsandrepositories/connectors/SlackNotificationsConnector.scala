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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.ws.writeableOf_JsValue
import play.api.{Configuration, Logger}
import play.api.libs.json._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SlackNotificationsConnector @Inject()(
  httpClientV2  : HttpClientV2,
  configuration : Configuration,
  servicesConfig: ServicesConfig,
)(implicit ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(this.getClass.getName)

  val url: String = servicesConfig.baseUrl("slack-notifications")

  private val internalAuthToken = configuration.get[String]("internal-auth.token")

  def sendMessage(message: SlackNotificationRequest): Future[SlackNotificationResponse] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val snrW: OWrites[SlackNotificationRequest] = SlackNotificationRequest.writes
    implicit val snrR: Reads[SlackNotificationResponse] = SlackNotificationResponse.reads
    httpClientV2
      .post(url"$url/slack-notifications/v2/notification")
      .withBody(Json.toJson(message))
      .setHeader("Authorization" -> internalAuthToken)
      .execute[SlackNotificationResponse]
      .recoverWith {
        case NonFatal(ex) =>
          logger.error(s"Unable to notify ${message.channelLookup} on Slack", ex)
          Future.failed(ex)
      }
  }
}

final case class SlackNotificationError(
  code   : String,
  message: String
)

final case class SlackNotificationResponse(
  errors: List[SlackNotificationError]
)

object SlackNotificationResponse {
  val reads: Reads[SlackNotificationResponse] = {
    implicit val sneReads: Reads[SlackNotificationError] =
      ( (__ \ "code").read[String]
      ~ (__ \ "message").read[String]
      )(SlackNotificationError.apply _)

    (__ \ "errors")
      .readWithDefault[List[SlackNotificationError]](List.empty)
      .map(SlackNotificationResponse.apply)
  }
}

sealed trait ChannelLookup {
  def by: String
}

object ChannelLookup {

  final case class RepositoryChannel(
    repositoryName: String,
    by            : String = "github-repository"
  ) extends ChannelLookup

  final case class SlackChannel(
    slackChannels: List[String],
    by           : String = "slack-channel"
  ) extends ChannelLookup

  val writes: Writes[ChannelLookup] =
    Writes {
      case s: SlackChannel      => Json.toJson(s)(Json.writes[SlackChannel])
      case s: RepositoryChannel => Json.toJson(s)(Json.writes[RepositoryChannel])
    }
}

final case class SlackNotificationRequest(
  channelLookup: ChannelLookup,
  displayName  : String,
  emoji        : String,
  text         : String,
  blocks       : Seq[JsValue]
)

object SlackNotificationRequest {
  val writes: OWrites[SlackNotificationRequest] = {
    implicit val clw = ChannelLookup.writes
    Json.writes[SlackNotificationRequest]
  }
}
