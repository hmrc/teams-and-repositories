/*
 * Copyright 2021 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.controller.model.Team
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister

import scala.concurrent.ExecutionContext

@Singleton
class TeamsController @Inject()(
  teamsAndReposPersister: TeamsAndReposPersister,
  configuration         : Configuration,
  cc                    : ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(cc) {

  lazy val repositoriesToIgnore: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  private implicit val tf = Team.format

  // /api/teams
  // equivalent to /api/teams_with_details/:team?exclude=repos,ownedRepos
  def teams = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        val teams =
          allTeamsAndRepos
            .map(_.toTeam(repositoriesToIgnore, excludeRepos = true))
        Ok(toJson(teams))
      }
  }

  // /api/teams/:team
  // equivalent to /api/teams_with_details/:team/repos
  def repositoriesByTeam(teamName: String) = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        val optTeam: Option[Team] =
          allTeamsAndRepos
            .find(_.teamName.equalsIgnoreCase(teamName))
            .map(_.toTeam(repositoriesToIgnore, excludeRepos = false))
        optTeam match {
          case None       => NotFound
          case Some(team) => Ok(toJson(team.repos.getOrElse(Map.empty))(Team.mapFormat))
        }
      }
  }

  // /api/teams_with_details/:team
  def repositoriesWithDetailsByTeam(teamName: String) = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        val optTeam: Option[Team] =
          allTeamsAndRepos
            .find(_.teamName.equalsIgnoreCase(teamName))
            .map(_.toTeam(repositoriesToIgnore, excludeRepos = false))
        optTeam match {
          case None       => NotFound
          case Some(team) => Ok(toJson(team))
        }
      }
  }

  // /api/teams_with_repositories
  // missing firstActiveDate, lastActiveDate, firstServiceCreationDate to be consistent with /api/teams/:team and /api/teams_with_details/:team
  def allTeamsAndRepositories = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      val teams =
        allTeamsAndRepos
          .map(_.toTeam(repositoriesToIgnore, excludeRepos = false))
      Ok(toJson(teams))
    }
  }
}
