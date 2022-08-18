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

package uk.gov.hmrc.teamsandrepositories.controller.v2

import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.{BuildJob, GitRepository, RepoType, ServiceType, TeamName}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence
import uk.gov.hmrc.teamsandrepositories.services.{BranchProtectionService, RebuildService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsAndRepositoriesController @Inject()(
  repositoriesPersistence: RepositoriesPersistence,
  branchProtectionService: BranchProtectionService,
  rebuildService: RebuildService ,
  auth: BackendAuthComponents,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  implicit val grf = GitRepository.apiFormat
  implicit val tnf = TeamName.apiFormat
  private implicit val bjw: Writes[BuildJob] = BuildJob.apiWrites

  def allRepos(name: Option[String], team: Option[String], archived: Option[Boolean], repoType: Option[RepoType], serviceType: Option[ServiceType]) = Action.async { request =>
    repositoriesPersistence.search(name, team, archived, repoType, serviceType)
      .map(result => Ok(Json.toJson(result.sortBy(_.name))))
  }

  def allTeams() = Action.async { request =>
    repositoriesPersistence.findTeamNames().map(result => Ok(Json.toJson(result)))
  }

  def findRepo(repoName:String) = Action.async { request =>
    repositoriesPersistence.findRepo(repoName).map {
      case None       => NotFound
      case Some(repo) => Ok(Json.toJson(repo))
    }
  }

  def enableBranchProtection(repoName: String): Action[JsValue] = {
    auth
      .authorizedAction(
        Predicate.Permission(
          Resource.from("catalogue-repository", s"$repoName"),
          IAAction("WRITE_BRANCH_PROTECTION")))
      .async[JsValue](parse.json) { implicit request =>
        val payload =
          implicitly[Reads[Boolean]].reads(request.body)

        payload.fold(
          errors => Future.successful(BadRequest(Json.stringify(JsError.toJson(errors)))),
          enabled =>
            if (enabled)
              branchProtectionService
                .enableBranchProtection(repoName)
                .map(_ => Ok)
            else
              Future.successful(BadRequest("Disabling branch protection is not currently supported.")))
    }
  }

  def getJobsWithNoBuildFor(daysUnbuilt: Int) = Action.async { _ =>
    rebuildService.getJobsWithNoBuildFor(daysUnbuilt).map {
      case list if list.isEmpty => NotFound
      case list => Ok(Json.toJson(list))
    }
  }
}
