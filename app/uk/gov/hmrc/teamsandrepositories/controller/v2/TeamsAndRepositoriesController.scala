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

import play.api.libs.json._
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence
import uk.gov.hmrc.teamsandrepositories.services.BranchProtectionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsAndRepositoriesController @Inject()(
  repositoriesPersistence: RepositoriesPersistence,
  branchProtectionService: BranchProtectionService,
  auth                   : BackendAuthComponents,
  cc                     : ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  implicit val grf = GitRepository.apiFormat
  implicit val tnf = TeamSummary.apiFormat

  private val logger = play.api.Logger(this.getClass)

  def allRepos(
    name       : Option[String],
    team       : Option[String],
    archived   : Option[Boolean],
    repoType   : Option[RepoType],
    serviceType: Option[ServiceType],
    tags       : Option[List[Tag]],
  ) = Action.async { request =>
    val uuid = java.util.UUID.randomUUID.toString
    val t0 = System.nanoTime()
    repositoriesPersistence.search(name, team, archived, repoType, serviceType, tags)
      .map { result =>
        val t1 = System.nanoTime()
        logger.info(s"${request.uri} $uuid - db query took ${(t1 - t0) / 1000000} ms" )
        val t2 = System.nanoTime()
        val x  = Ok(Json.toJson(result.sortBy(_.name)))
        val t3 = System.nanoTime()
        logger.info(s"${request.uri} $uuid - json formatting took ${(t3 - t2) / 1000000} ms" )
        logger.info(s"${request.uri} $uuid - total took ${(t3 - t0) / 1000000} ms" )
        x
      }
  }

  def allTeams() = Action.async { request =>
    repositoriesPersistence.findTeamSummaries().map(result => Ok(Json.toJson(result)))
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
}
