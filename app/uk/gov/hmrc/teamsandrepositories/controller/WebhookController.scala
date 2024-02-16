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
import uk.gov.hmrc.teamsandrepositories.controller.WebhookController._
import uk.gov.hmrc.teamsandrepositories.models.DeletedGitRepository
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence}
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WebhookController @Inject()(
  persistingService             : PersistingService,
  repositoriesPersistence       : RepositoriesPersistence,
  deletedRepositoriesPersistence: DeletedRepositoriesPersistence,
  cc                            : ControllerComponents
)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def processGithubWebhook(): Action[GithubRequest] = Action.apply(parse.json[GithubRequest](GithubRequest.githubReads)) { implicit request =>
    def updateRepositoryForAction(repoName: String, message: String, action: String): Future[Unit] = {
      persistingService.updateRepository(repoName).fold(
        err => logger.info(s"repo: $repoName - failed action: $action - $err"),
        _   => logger.info(s"repo: $repoName - $message")
      ).recover {
        case ex => logger.error(s"repo: $repoName - $action - $ex", ex)
      }
    }

    def deletedRepositoryEvent(repoName: String): Future[Unit] =
      repositoriesPersistence.findRepo(repoName).flatMap {
        case Some(repo) =>
          for {
            _   <- deletedRepositoriesPersistence.set(DeletedGitRepository.fromGitRepository(repo, Instant.now()))
            _   <- persistingService.repositoryDeleted(repoName)
          } yield ()
        case None =>
          deletedRepositoriesPersistence.set(DeletedGitRepository(repoName, Instant.now()))
      }.recover {
        case ex: com.mongodb.MongoWriteException if ex.getMessage.contains("duplicate key error") =>
          logger.info(s"repository: $repoName already stored in deleted-repositories collection")
        case ex =>
          logger.warn(s"Unexpected error when storing deleted repository $repoName - ${ex.getMessage}", ex)
      }

    request.body match {
      case push: Push if push.branchRef == "main" =>
        updateRepositoryForAction(push.repoName, "successfully updated repo", "push")
        Accepted(details("Push accepted"))

      case push: Push =>
        logger.info(s"repo: ${push.repoName} branch: ${push.branchRef} - no change required for push event")
        Ok(details("No change required for push event"))

      case teamEvent: TeamEvent if teamEvent.action.equalsIgnoreCase("added_to_repository") =>
        updateRepositoryForAction(teamEvent.repoName, s"successfully added team: ${teamEvent.teamName}", teamEvent.action)
        Accepted(details("Team added event accepted"))

      case teamEvent: TeamEvent if teamEvent.action.equalsIgnoreCase("removed_from_repository") =>
        updateRepositoryForAction(teamEvent.repoName, s"successfully removed team: ${teamEvent.teamName}", teamEvent.action)
        Accepted(details("Team removal event accepted"))

      case teamEvent: TeamEvent =>
        logger.info(s"repo: ${teamEvent.repoName} - team event: ${teamEvent.action} are ignored")
        Ok(details(s"Team events for: ${teamEvent.action} are ignored"))

      case repositoryEvent: RepositoryEvent if repositoryEvent.action.equalsIgnoreCase("archived") =>
        persistingService.repositoryArchived(repositoryEvent.repoName)
        logger.info(s"repo: ${repositoryEvent.repoName} - repository archived webhook event has been actioned")
        Accepted(details(s"Repository archived event accepted"))

      case repositoryEvent: RepositoryEvent if repositoryEvent.action.equalsIgnoreCase("deleted") =>
        logger.info(s"repo: ${repositoryEvent.repoName} - repository deleted webhook event has been actioned")
        deletedRepositoryEvent(repositoryEvent.repoName)
        Accepted(details(s"Repository deleted event accepted"))

      case repositoryEvent: RepositoryEvent =>
        Ok(details(s"Repository events with ${repositoryEvent.action} actions are ignored"))
    }
  }

  private def details(msg: String): JsObject =
    Json.obj("details" -> msg)
}

object WebhookController {
  sealed trait GithubRequest

  object GithubRequest {
    val githubReads: Reads[GithubRequest] =
      Push.reads
        .orElse(TeamEvent.reads)
        .orElse(RepositoryEvent.reads)
  }

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#team
  final case class TeamEvent(
    teamName: String,
    repoName: String,
    action  : String
  ) extends GithubRequest

  object TeamEvent {
    val reads: Reads[GithubRequest] =
      ( (__ \ "team" \ "name"      ).read[String]
      ~ (__ \ "repository" \ "name").read[String]
      ~ (__ \ "action"             ).read[String]
      )(TeamEvent.apply _)
  }

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
  final case class Push(
    repoName: String,
    branchRef: String
  ) extends GithubRequest

  object Push {
    val reads: Reads[GithubRequest] =
      ( (__ \ "repository" \ "name").read[String]
      ~ (__ \ "ref"                ).read[String].map(_.stripPrefix("refs/heads/"))
      )(Push.apply _)
  }

  // https://docs.github.com/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#repository
  final case class RepositoryEvent(
    repoName: String,
    action  : String
  ) extends GithubRequest

  object RepositoryEvent {
    val reads: Reads[GithubRequest] =
      ( (__ \ "repository" \ "name").read[String]
      ~ (__ \ "action"             ).read[String]
      )(RepositoryEvent.apply _)
  }
}
