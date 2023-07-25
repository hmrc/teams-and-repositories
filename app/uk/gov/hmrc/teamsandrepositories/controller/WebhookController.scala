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

package uk.gov.hmrc.teamsandrepositories.controller

import akka.actor.ActorSystem
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.services.PersistingService
import cats.implicits._
import cats.data.EitherT
import play.api.{Configuration, Logging}
import play.api.mvc.ControllerComponents

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebhookController @Inject()(
  config           : Configuration,
  persistingService: PersistingService,
  cc               : ControllerComponents
)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
  case class Push(repoName: String, branchRef: String)

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  private implicit val readsPush: Reads[Push] =
    ( (__ \ "repository" \ "name").read[String]
    ~ (__ \ "ref"                ).read[String].map(_.stripPrefix("refs/heads/"))
    )(Push.apply _)

  def processGithubWebhook() =
    Action.apply(parse.json[Push]) { implicit request =>
      logger.info(s"Webhook payload for ${request.body.repoName}: ${request.body}")
      (request.body match {
        case Push(repo, "main") =>
          val delayInSeconds = 8
          val scheduler = ActorSystem("scheduler")
          scheduler.scheduler.scheduleOnce(delayInSeconds.seconds) {
            persistingService.updateRepository(repo)
          }
          EitherT.rightT[Future, Unit](())
        case _                  => EitherT.leftT[Future, Unit]("no change required")
      }).fold(
        err => logger.info(s"repo: ${request.body.repoName} branch: ${request.body.branchRef} - $err")
      , _   => logger.info(s"repo: ${request.body.repoName} branch: ${request.body.branchRef} - successfully created repo")
      ).recover {
        case ex => logger.error(s"repo: ${request.body.repoName} branch: ${request.body.branchRef} - $ex", ex)
      }
      Accepted(details("Push accepted"))
    }

  private def details(msg: String) =
    Json.obj("details" -> msg)

}
