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

import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, TeamName}
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence
import uk.gov.hmrc.teamsandrepositories.services.BranchProtectionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TeamsAndRepositoriesController @Inject()(
  repositoriesPersistence: RepositoriesPersistence,
  branchProtectionService: BranchProtectionService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  implicit val grf = GitRepository.apiFormat
  implicit val tnf = TeamName.apiFormat

  def allRepos(team: Option[String], archived: Option[Boolean], repoType: Option[RepoType]) = Action.async { request =>
    repositoriesPersistence.search(team, archived, repoType)
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

  def setBranchProtection(repoName: String) = Action.async { _ =>
    branchProtectionService
      .setBranchProtection(repoName)
      .map(_ => Ok)
  }

}
