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
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

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

  def teams = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.getTeamList(allTeamsAndRepos, repositoriesToIgnore)))
    }
  }

  def repositoriesByTeam(teamName: String) = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      TeamRepositories.getTeamRepositoryNameList(allTeamsAndRepos, teamName) match {
        case None    => NotFound
        case Some(x) => Ok(toJson(x.map { case (t, v) => (t.toString, v) }))
      }
    }
  }

  def repositoriesWithDetailsByTeam(teamName: String) = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      TeamRepositories.findTeam(allTeamsAndRepos, teamName, repositoriesToIgnore) match {
        case None    => NotFound
        case Some(x) => Ok(toJson(x))
      }
    }
  }

  def allTeamsAndRepositories = Action.async {
    teamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.allTeamsAndTheirRepositories(allTeamsAndRepos, repositoriesToIgnore)))
    }
  }
}
