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

package uk.gov.hmrc.teamsandrepositories.controller.v2

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence, TeamSummaryPersistence}
import uk.gov.hmrc.teamsandrepositories.services.BranchProtectionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsAndRepositoriesController @Inject()(
  repositoriesPersistence       : RepositoriesPersistence,
  teamSummaryPersistence        : TeamSummaryPersistence,
  deletedRepositoriesPersistence: DeletedRepositoriesPersistence,
  branchProtectionService       : BranchProtectionService,
  auth                          : BackendAuthComponents,
  cc                            : ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  implicit val grf: Format[GitRepository] = GitRepository.apiFormat
  implicit val tnf: Format[TeamSummary]   = TeamSummary.apiFormat

  def repositories(
    name       : Option[String],
    team       : Option[String],
    owningTeam : Option[String],
    archived   : Option[Boolean],
    repoType   : Option[RepoType],
    serviceType: Option[ServiceType],
    tags       : Option[List[Tag]],
  ) = Action.async { request =>
    repositoriesPersistence.find(name, team, owningTeam, archived, repoType, serviceType, tags)
      .map(result => Ok(Json.toJson(result.sortBy(_.name))))
  }

  def allTeams() = Action.async { request =>
    teamSummaryPersistence.findTeamSummaries()
      .map(result => Ok(Json.toJson(result)))
  }

  def findRepo(repoName:String) = Action.async { request =>
    repositoriesPersistence.findRepo(repoName).map {
      case None       => NotFound
      case Some(repo) => Ok(Json.toJson(repo))
    }
  }

  def enableBranchProtection(repoName: String): Action[JsValue] =
    auth
      .authorizedAction(
        Predicate.Permission(
          Resource.from("catalogue-repository", s"$repoName"),
          IAAction("WRITE_BRANCH_PROTECTION")))
      .async[JsValue](parse.json) { implicit request =>
        val payload =
          implicitly[Reads[Boolean]].reads(request.body)

        payload.fold(
          errors  => Future.successful(BadRequest(Json.stringify(JsError.toJson(errors)))),
          enabled =>
            if (enabled)
              branchProtectionService
                .enableBranchProtection(repoName)
                .map(_ => Ok)
            else
              Future.successful(BadRequest("Disabling branch protection is not currently supported.")))
    }

  def decommissionedRepos(repoType: Option[RepoType] = None) = Action.async { request =>
    for {
      archivedRepos  <- repositoriesPersistence.find(isArchived = Some(true), repoType = repoType)
                          .map(_.map(repo => DecommissionedRepo(repo.name, Some(repo.repoType))))
      deletedRepos   <- deletedRepositoriesPersistence.find(repoType = repoType)
                          .map(_.map(repo => DecommissionedRepo(repo.name, repo.repoType)))
      decommissioned =  (archivedRepos ++ deletedRepos)
                          .distinct
                          .sortBy(_.repoName.toLowerCase)
    } yield Ok(Json.toJson(decommissioned))
  }
}

case class DecommissionedRepo(
  repoName: String,
  repoType: Option[RepoType]
)

object DecommissionedRepo {
  implicit val apiWrites: Writes[DecommissionedRepo] =
    ( (__ \ "repoName").write[String]
    ~ (__ \ "repoType").writeNullable[RepoType](RepoType.format)
    )(unlift(DecommissionedRepo.unapply))
}
