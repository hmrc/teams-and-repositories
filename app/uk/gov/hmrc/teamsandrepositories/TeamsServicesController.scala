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

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.util.concurrent.Executors

import play.api.libs.json.Json
import play.api.mvc.{Results, _}
import play.libs.Akka
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.teamsandrepositories.config._

import scala.concurrent.{ExecutionContext, Future}

case class Environment(name: String, services: Seq[Link])

case class Link(name: String, displayName: String, url: String)


case class RepositoryDetails(name: String, repoType: RepoType.RepoType, teamNames: Seq[String], githubUrls: Seq[Link], ci: Seq[Link] = Seq.empty, environments: Seq[Environment] = Seq.empty)


object BlockingIOExecutionContext {
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))
}

object TeamsServicesController extends TeamsServicesController
with UrlTemplatesProvider {

  private val gitApiEnterpriseClient = GithubApiClient(GithubConfig.githubApiEnterpriseConfig.apiUrl, GithubConfig.githubApiEnterpriseConfig.key)

  private val enterpriseTeamsRepositoryDataSource: RepositoryDataSource =
    new GithubV3RepositoryDataSource(gitApiEnterpriseClient, isInternal = true) with GithubConfigProvider

  private val gitOpenClient = GithubApiClient(GithubConfig.githubApiOpenConfig.apiUrl, GithubConfig.githubApiOpenConfig.key)
  private val openTeamsRepositoryDataSource: RepositoryDataSource =
    new GithubV3RepositoryDataSource(gitOpenClient, isInternal = false) with GithubConfigProvider

  private def dataLoader: () => Future[Seq[TeamRepositories]] = new CompositeRepositoryDataSource(List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)).getTeamRepoMapping _

  protected val dataSource: CachingRepositoryDataSource[Seq[TeamRepositories]] = new CachingRepositoryDataSource[Seq[TeamRepositories]](
    Akka.system(), CacheConfig,
    dataLoader,
    LocalDateTime.now
  )
}

trait TeamsServicesController extends BaseController {

  import TeamRepositoryWrapper._

  protected def ciUrlTemplates: UrlTemplates

  protected def dataSource: CachingRepositoryDataSource[Seq[TeamRepositories]]

  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  private val ServiceDetailsContentType = Accepting("application/vnd.servicedetails.hal+json")
  private val CachedTeamsAction = CachedTeamsActionBuilder(dataSource.getCachedTeamRepoMapping _)


  def repositoryDetails(name: String) = CachedTeamsAction { implicit request =>
    request.teams.findRepositoryDetails(name, ciUrlTemplates) match {
      case None => NotFound
      case Some(x) => Results.Ok(Json.toJson(x))
    }
  }

  def services() = CachedTeamsAction { implicit request =>
    render {
      case Accepts.Json() => Results.Ok(Json.toJson(request.teams.asServiceNameList))
      case ServiceDetailsContentType() => Results.Ok(Json.toJson(request.teams.asRepositoryDetailsList(RepoType.Deployable, ciUrlTemplates)))
    }
  }

  def libraries() = CachedTeamsAction { implicit request =>
    Results.Ok(Json.toJson(request.teams.asLibraryNameList))
  }


  def teams() = CachedTeamsAction { implicit request =>
    Results.Ok(Json.toJson(request.teams.asTeamNameList))
  }

  def team(teamName: String) = CachedTeamsAction { implicit request =>
    request.teams.asTeamRepositoryNameList(teamName) match {
      case None => NotFound
      case Some(x) => Results.Ok(Json.toJson(x.map { case (t, v) =>
        (t.toString, v)
      }))
    }
  }

  def reloadCache() = Action { implicit request =>
    dataSource.reload()
    Ok("Cache reload triggered successfully")
  }
}
