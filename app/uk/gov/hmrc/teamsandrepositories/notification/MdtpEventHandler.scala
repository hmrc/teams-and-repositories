/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.{Configuration, Logging}
import play.api.libs.json.Json
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.hmrc.teamsandrepositories.service.JenkinsReloadService

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.teamsandrepositories.connector.JenkinsConnector.LatestBuild

@Singleton
class MdtpEventHandler @Inject()(
  configuration       : Configuration
, jenkinsReloadService: JenkinsReloadService
)(using
  actorSystem    : ActorSystem
, ec             : ExecutionContext
) extends SqsConsumer(
  name           = "MDTP Event"
, config         = SqsConfig("aws.sqs.mdtpEvent", configuration)
)(using actorSystem, ec) with Logging:

  override protected def processMessage(message: Message): Future[MessageAction] =
    logger.info(s"Starting processing MDTP Event message with ID '${message.messageId()}'")
    val eitherTResult: EitherT[Future, String, MessageAction] =
      for 
        payload <- EitherT.fromEither[Future](
                     Json
                       .parse(message.body)
                       .validate(MdtpEventHandler.MdtpEvent.reads)
                       .asEither.left.map(error => s"Could not parse message with ID '${message.messageId()}' and body: ${message.body}. Reason: " + error.toString)
                   )
        id      =  s"MDTP Event message with ID '${message.messageId()}' type: ${payload.`type`} subtype: ${payload.subtype} and details: ${payload.json}"
        action  <- (payload.`type`, payload.subtype) match
                     case ("jenkins-build", Some("finished")) =>
                       for
                         event <- EitherT.fromEither[Future](
                                    payload
                                      .json
                                      .validate(MdtpEventHandler.JenkinsBuildEvent.reads)
                                      .asEither.left.map(error => s"Could not parse $id. Reason: $error")
                                  )
                         _     <- EitherT(
                                    jenkinsReloadService
                                      .updateJob(event)
                                      .map(Right.apply)
                                      .recover { case e => logger.error(s"Could not process $id", e); Left(s"Could not process $id ${e.getMessage}")}
                                  )
                       yield
                         logger.info(s"Successfully processed $id")
                         MessageAction.Delete(message)
                     case _ =>
                       logger.info(s"Unknown $id - remove from queue")
                       EitherT.pure[Future, String](MessageAction.Delete(message))
      yield action

    eitherTResult.fold(
      error  => { logger.error(error); MessageAction.Ignore(message) },
      action => action
    )


object MdtpEventHandler:
  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Reads, JsValue, __}
  case class MdtpEvent(
    id      : String
  , `type`  : String
  , subtype : Option[String]
  , dateTime: Instant
  , json    : JsValue
  )

  object MdtpEvent:
    // https://github.com/hmrc/build-and-deploy/blob/3b1f0cf96af48062af118b5ce6952ddd22877fd2/products/api/docs/json-schemas/mdtp_events_schema.json#L22
    val reads: Reads[MdtpEvent] =
      ( (__ \ "id"       ).read[String]
      ~ (__ \ "type"     ).read[String]
      ~ (__ \ "subtype"  ).readNullable[String]
      ~ (__ \ "date_time").read[Instant]
      ~ (__ \ "details"  ).read[JsValue]
      )(MdtpEvent.apply _)

  case class JenkinsBuildEvent(
    jobFullName: String // including folder
  , jobName    : String
  , jobUrl     : String
  , githubUrl  : String
  , buildNumer : Int
  , buildUrl   : String
  , buildFinish: Instant
  , buildStart : Instant
  , result     : LatestBuild.BuildResult
  )

  object JenkinsBuildEvent:
    given Reads[LatestBuild.BuildResult] = LatestBuild.BuildResult.format
    // https://github.com/hmrc/build-and-deploy/blob/3b1f0cf96af48062af118b5ce6952ddd22877fd2/products/api/docs/json-schemas/mdtp_events_jenkins_build_schema.json
    val reads: Reads[JenkinsBuildEvent] =
      ( (__ \ "job_full_name"         ).read[String]
      ~ (__ \ "job_name"              ).read[String]
      ~ (__ \ "job_url"               ).read[String]
      ~ (__ \ "job_github_url"        ).read[String]
      ~ (__ \ "build_number"          ).read[Int]
      ~ (__ \ "build_url"             ).read[String]
      ~ (__ \ "build_finish_timestamp").read[Instant]
      ~ (__ \ "build_start_timestamp" ).read[Instant]
      ~ (__ \ "result"                ).read[LatestBuild.BuildResult]
      )(JenkinsBuildEvent.apply _)
