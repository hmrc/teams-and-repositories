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

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

import play.api.libs.json.Json
import play.api.mvc.{Results, _}
import play.libs.Akka
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.teamsandrepositories.config._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}



case class Environment(name: String, services: Seq[Link])

case class Link(name: String, displayName: String, url: String)

case class RepositoryDetails(name: String,
                             description: String,
                             createdAt: Long,
                             lastActive: Long,
                             repoType: RepoType.RepoType,
                             teamNames: Seq[String],
                             githubUrls: Seq[Link],
                             ci: Seq[Link] = Seq.empty,
                             environments: Seq[Environment] = Seq.empty)

case class RepositoryDisplayDetails(name:String, createdAt: Long, lastUpdatedAt: Long)
object RepositoryDisplayDetails {
  implicit val repoDetailsFormat = Json.format[RepositoryDisplayDetails]
}

object BlockingIOExecutionContext {
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))
}

object TeamsRepositoriesController extends TeamsRepositoriesController
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

trait TeamsRepositoriesController extends BaseController {

  import TeamRepositoryWrapper._

  protected def ciUrlTemplates: UrlTemplates

  protected def dataSource: CachingRepositoryDataSource[Seq[TeamRepositories]]

  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]

  val CacheTimestampHeaderName = "X-Cache-Timestamp"

  private def format(dateTime: LocalDateTime): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(dateTime, ZoneId.of("GMT")))
  }

  def repositoryDetails(name: String) = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { cachedTeams =>
      (cachedTeams.data.findRepositoryDetails(name, ciUrlTemplates) match {
        case None => NotFound
        case Some(x: RepositoryDetails) => Results.Ok(Json.toJson(x))
      }).withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  def services() = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { (cachedTeams: CachedResult[Seq[TeamRepositories]]) =>
      Ok(determineServicesResponse(request, cachedTeams.data))
        .withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }



  import RepositoryDisplayDetails._
  private def determineServicesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]) =
    if (request.getQueryString("details").nonEmpty)
      Json.toJson(data.asRepositoryDetailsList(RepoType.Deployable, ciUrlTemplates))
    else if (request.getQueryString("teamDetails").nonEmpty)
      Json.toJson(data.asRepositoryTeamNameList())
    else Json.toJson(data.asServiceRepoDetailsList)

  def libraries() = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { cachedTeams =>
      Ok(determineLibrariesResponse(request, cachedTeams.data))
        .withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  private def determineLibrariesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]) = {
    if (request.getQueryString("details").nonEmpty)
      Json.toJson(data.asRepositoryDetailsList(RepoType.Library, ciUrlTemplates))
    else
      Json.toJson(data.asLibraryRepoDetailsList)
  }

  def teams() = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { cachedTeams =>
      Results.Ok(Json.toJson(cachedTeams.data.asTeamNameList))
        .withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  def repositoriesByTeam(teamName: String) = Action.async { implicit request =>
    dataSource.getCachedTeamRepoMapping.map { cachedTeams =>
      (cachedTeams.data.asTeamRepositoryNameList(teamName) match {
        case None => NotFound
        case Some(x) => Results.Ok(Json.toJson(x.map { case (t, v) => (t.toString, v) }))
      }).withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
    }
  }

  def reloadCache() = Action { implicit request =>
    dataSource.reload()
    Ok("Cache reload triggered successfully")
  }
}
