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

import play.api.Logging
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsObject, Json, Reads, __}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.controller.WebhookController.*
import uk.gov.hmrc.teamsandrepositories.model.TeamSummary
import uk.gov.hmrc.teamsandrepositories.service.PersistingService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebhookController @Inject()(
  persistingService: PersistingService,
  cc               : ControllerComponents
)(using ExecutionContext) extends BackendController(cc) with Logging:

  def processGithubWebhook(): Action[GithubRequest] = Action.apply(parse.json[GithubRequest](GithubRequest.githubReads)) { implicit request =>
    def updateRepositoryForAction(repoName: String, message: String, action: String): Future[Unit] =
      persistingService.updateRepository(repoName).fold(
        err => logger.info(s"repo: $repoName - failed action: $action - $err"),
        _   => logger.info(s"repo: $repoName - $message")
      ).recover:
        case ex => logger.error(s"repo: $repoName - $action - $ex", ex)

    request.body match
      case push: Push if push.branchRef == "main" =>
        updateRepositoryForAction(push.repoName, "successfully updated repo", "push")
        Accepted(details("Push accepted"))

      case push: Push =>
        logger.info(s"repo: ${push.repoName} branch: ${push.branchRef} - no change required for push event")
        Ok(details("No change required for push event"))

      case TeamEvent("created", teamName, _) =>
        persistingService
          .addTeam(TeamSummary(teamName, Seq.empty))
          .map(_ => logger.info(s"New team created: $teamName - New team created webhook event has been actioned"))
          .recover { case ex => logger.error(s"New team: $teamName - unexpected error updating teams", ex) }
        Accepted(details("Team creation event accepted"))

      case TeamEvent("added_to_repository", teamName, Some(repositoryName)) =>
        updateRepositoryForAction(repositoryName, s"successfully added team: $teamName", "added_to_repository")
        Accepted(details("Team added event accepted"))

      case TeamEvent("removed_from_repository", teamName, Some(repositoryName)) =>
        updateRepositoryForAction(repositoryName, s"successfully removed team: $teamName", "removed_from_repository")
        Accepted(details("Team removal event accepted"))

      case teamEvent: TeamEvent =>
        logger.info(s"repo: ${teamEvent.repoName} - team event: ${teamEvent.action} are ignored")
        Ok(details(s"Team events for: ${teamEvent.action} are ignored"))

      case RepositoryEvent("archived", repositoryName) =>
        persistingService
          .archiveRepository(repositoryName)
          .map(_ => logger.info(s"repo: $repositoryName - repository archived webhook event has been actioned"))
          .recover { case ex => logger.error(s"repo: $repositoryName - unexpected error archiving repository", ex) }
        Accepted(details(s"Repository archived event accepted"))

      case RepositoryEvent("deleted", repositoryName) =>
        persistingService
          .deleteRepository(repositoryName)
          .map(_ => logger.info(s"repo: $repositoryName - repository deleted webhook event has been actioned"))
          .recover { case ex => logger.error(s"repo: $repositoryName - unexpected error archiving repository", ex) }
        Accepted(details(s"Repository deleted event accepted"))

      case repositoryEvent: RepositoryEvent =>
        Ok(details(s"Repository events with ${repositoryEvent.action} actions are ignored"))
  }

  private def details(msg: String): JsObject =
    Json.obj("details" -> msg)

object WebhookController:
  sealed trait GithubRequest

  object GithubRequest:
    val githubReads: Reads[GithubRequest] =
      Push.reads
        .orElse(TeamEvent.reads)
        .orElse(RepositoryEvent.reads)

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#team
  case class TeamEvent(
    action: String,
    teamName: String,
    repoName: Option[String]
  ) extends GithubRequest

  object TeamEvent:
    val reads: Reads[GithubRequest] =
      ( (__ \ "action"             ).read[String].map(_.toLowerCase)
      ~ (__ \ "team" \ "name"      ).read[String]
      ~ (__ \ "repository" \ "name").readNullable[String]
      )(TeamEvent.apply _)

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
  case class Push(
    repoName: String,
    branchRef: String
  ) extends GithubRequest

  object Push:
    val reads: Reads[GithubRequest] =
      ( (__ \ "repository" \ "name").read[String]
      ~ (__ \ "ref"                ).read[String].map(_.stripPrefix("refs/heads/"))
      )(Push.apply _)

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#repository
  case class RepositoryEvent(
    action: String,
    repoName: String
  ) extends GithubRequest

  object RepositoryEvent:
    val reads: Reads[GithubRequest] =
      ( (__ \ "action"             ).read[String].map(_.toLowerCase)
      ~ (__ \ "repository" \ "name").read[String]
      )(RepositoryEvent.apply _)
