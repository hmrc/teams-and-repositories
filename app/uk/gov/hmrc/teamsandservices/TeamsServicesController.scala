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

package uk.gov.hmrc.teamsandservices

import org.joda.time.DateTime
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Action
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.teamsandservices.DataSourceToApiContractMappings._
import uk.gov.hmrc.teamsandservices.config._


case class Link(name: String, url: String)
case class TeamServices(teamName: String, Services: List[Service])
case class Service(name: String, teamNames: Seq[String], githubUrls: Seq[Link], ci: List[Link])

object TeamsServicesController extends TeamsServicesController
  with UrlTemplatesProvider
{
  private val gitApiEnterpriseClient = new GithubApiClient(GithubConfig.githubApiEnterpriseConfig)
  private val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
    new GithubV3RepositoryDataSource(gitApiEnterpriseClient, isInternal = true) with GithubConfigProvider

  private val gitOpenClient = new GithubApiClient(GithubConfig.githubApiOpenConfig)
  private val openTeamsRepositoryDataSource: RepositoryDataSource =
    new GithubV3RepositoryDataSource(gitOpenClient, isInternal = false) with GithubConfigProvider

  protected val dataSource: CachingRepositoryDataSource = new CachingRepositoryDataSource(
    new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)),
    DateTime.now
  ) with CacheConfigProvider
}

trait TeamsServicesController extends BaseController {
  protected def ciUrlTemplates: UrlTemplates
  protected def dataSource: CachingRepositoryDataSource

  implicit val linkFormats = Json.format[Link]
  implicit val serviceFormats = Json.format[Service]
  implicit val teamFormats = Json.format[TeamServices]

  def services() = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map {
      teams => Ok(Json.toJson(teams.asServicesList(ciUrlTemplates)))
    }
  }

  def teams() = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map {
      teams => Ok(Json.toJson(teams.asTeamsList))
    }
  }

  def teamServices(teamName:String) = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { teams =>
      val cached = teams.asTeamServices(teamName, ciUrlTemplates)
      cached.data match {
        case Nil => NotFound
        case _ => Ok(Json.toJson(cached))
      }
    }
  }

  def reloadCache() = Action { implicit request =>
    dataSource.reload()
    Ok("Cache reload triggered successfully")
  }
}