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

package uk.gov.hmrc.teamsandrepositories.notification

import org.apache.pekko.actor.ActorSystem
import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Json
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MdtpEventHandler @Inject()(
  configuration  : Configuration
)(implicit
  actorSystem    : ActorSystem,
  ec             : ExecutionContext
) extends SqsConsumer(
  name           = "Mdtp Event"
, config         = SqsConfig("aws.sqs.mdtpEvent", configuration)
)(actorSystem, ec) {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override protected def processMessage(message: Message): Future[MessageAction] = {
    logger.info(s"Starting processing MDTP Event message with ID '${message.messageId()}'")
      (for {
         payload <- EitherT.fromEither[Future](
                      Json.parse(message.body)
                        .validate(MdtpEventHandler.MdtpEvent.reads)
                        .asEither.left.map(error => s"Could not parse message with ID '${message.messageId()}'.  Reason: " + error.toString)
                    )
        } yield {
          logger.info(s"MDTP Event ID '${message.messageId()} -  successfully processed: $payload")
          MessageAction.Delete(message)
        }
      ).value.map {
        case Left(error)   => logger.error(error)
                              MessageAction.Ignore(message)
        case Right(action) => action
      }
  }
}

object MdtpEventHandler {
  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Reads, JsValue, __}
  case class MdtpEvent(
    eventId  : String
  , eventType: String
  , status   : String
  , dateTime : Instant
  , details  : JsValue
  )

  object MdtpEvent {
    val reads: Reads[MdtpEvent] =
      ( (__ \ "event_id"  ).read[String]
      ~ (__ \ "event_type").read[String]
      ~ (__ \ "status"    ).read[String]
      ~ (__ \ "date_time" ).read[Instant]
      ~ (__ \ "details"   ).read[JsValue]
      )(MdtpEvent.apply _)
  }
}
