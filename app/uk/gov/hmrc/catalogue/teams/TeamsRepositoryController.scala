/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.catalogue.teams

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.catalogue.config.{CatalogueConfig, CacheConfigProvider}
import uk.gov.hmrc.catalogue.teams.ViewModels.{Service, TeamServices}
import uk.gov.hmrc.play.microservice.controller.BaseController

object TeamsRepositoryController extends TeamsRepositoryController
with GithubEnterpriseTeamsRepositoryDataSourceProvider with GithubOpenTeamsRepositoryDataSourceProvider
{
  val dataSource: CachingTeamsRepositoryDataSource = new CachingTeamsRepositoryDataSource(
    new CompositeTeamsRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource))
  ) with CacheConfigProvider
}


trait TeamsRepositoryController extends BaseController with CatalogueConfig {
  def dataSource: CachingTeamsRepositoryDataSource

  def teamRepository() = Action.async { implicit request =>
    dataSource.getTeamRepoMapping.map {
      teams => Ok(Json.toJson(teams))
    }
  }

  def services(teamName:String) = Action.async { implicit request =>
    dataSource.getTeamRepoMapping.map { teams =>
      teams.find(_.teamName == teamName).map { team =>
        val services = TeamServices(team.teamName, team.repositories.flatMap(Service.fromRepository(_)))
        Ok(Json.toJson(services))
      }.getOrElse(NotFound)

    }
  }

  def reloadCache() = Action { implicit request =>
    dataSource.reload()
    Ok("Cache reload triggered successfully")
  }
}
