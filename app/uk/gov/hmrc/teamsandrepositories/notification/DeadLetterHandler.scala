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
import javax.inject.{Inject, Singleton}
import software.amazon.awssdk.services.sqs.model.Message

import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration

@Singleton
class DeploymentDeadLetterHandler @Inject()(
  configuration: Configuration
)(implicit
  actorSystem  : ActorSystem,
  ec           : ExecutionContext
) extends SqsConsumer(
  name           = "Mdtp Event"
, config         = SqsConfig("aws.sqs.mdtpEventDeadLetter", configuration)
)(actorSystem, ec) {

  protected def processMessage(message: Message) = {
    logger.warn(
      s"""MDTP Event dead letter message with
         |ID: '${message.messageId}'
         |Body: '${message.body}'""".stripMargin
    )
    Future.successful(MessageAction.Delete(message))
  }
}
