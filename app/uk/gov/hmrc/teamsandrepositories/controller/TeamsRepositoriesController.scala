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

import java.net.URLDecoder
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.concurrent.Executors

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc.{Results, _}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories.DigitalService
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}
import uk.gov.hmrc.teamsandrepositories.controller.model.{Environment, Link, RepositoryDetails}
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global





object BlockingIOExecutionContext {
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))
}


@Singleton
class TeamsRepositoriesController @Inject()(dataReloadScheduler: DataReloadScheduler,
                                            teamsAndReposPersister: TeamsAndReposPersister,
                                            urlTemplatesProvider: UrlTemplatesProvider,
                                            configuration: Configuration,
                                            mongoTeamsAndReposPersister: TeamsAndReposPersister) extends BaseController {

  import scala.collection.JavaConverters._

  val TimestampHeaderName = "X-Cache-Timestamp"

  val repositoriesToIgnore: List[String] = configuration.getStringList("shared.repositories").fold(List.empty[String])(_.asScala.toList)

  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]


  def repositoryDetails(name: String) = Action.async {
    val repoName = URLDecoder.decode(name, "UTF-8")

    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      TeamRepositories.findRepositoryDetails(allTeamsAndRepos, repoName, urlTemplatesProvider.ciUrlTemplates) match {
        case None =>
          NotFound
        case Some(x: RepositoryDetails) =>
          Ok(Json.toJson(x))
      }
    }
  }

  def digitalServiceDetails(digitalServiceName: String) = Action.async {
    val sanitisedDigitalServiceName = URLDecoder.decode(digitalServiceName, "UTF-8")

    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      TeamRepositories.findDigitalServiceDetails(allTeamsAndRepos, sanitisedDigitalServiceName) match {
        case None =>
          NotFound
        case Some(x: DigitalService) =>
          Ok(Json.toJson(x))
      }
    }
  }


  def services() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      Ok(determineServicesResponse(request, allTeamsAndRepos))
    }
  }

  def libraries() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      Ok(determineLibrariesResponse(request, allTeamsAndRepos))
    }
  }

  def digitalServices() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>

      val digitalServices: Seq[String] =
        allTeamsAndRepos
          .flatMap(_.repositories)
          .flatMap(_.digitalServiceName)
          .distinct
          .sorted

      Ok(Json.toJson(digitalServices))
    }
  }

  def all() = Action.async{
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map(allRecords => Ok(Json.toJson(allRecords)))
  }

  def allRepositories() = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      Ok(Json.toJson(TeamRepositories.getAllRepositories(allTeamsAndRepos)))
    }
  }

  def teams() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      Ok(Json.toJson(TeamRepositories.getTeamList(allTeamsAndRepos, repositoriesToIgnore)))
    }
  }

  def repositoriesByTeam(teamName: String) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>

      TeamRepositories.getTeamRepositoryNameList(allTeamsAndRepos, teamName) match {
        case None => NotFound
        case Some(x) => Ok(Json.toJson(x.map { case (t, v) => (t.toString, v) }))
      }
    }
  }


  def repositoriesWithDetailsByTeam(teamName: String) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>

      TeamRepositories.findTeam(allTeamsAndRepos, teamName, repositoriesToIgnore) match {
        case None => NotFound
        case Some(x) => Ok(Json.toJson(x))
      }
    }
  }

  def allTeamsAndRepositories() = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos) =>
      Ok(Json.toJson(TeamRepositories.allTeamsAndTheirRepositories(allTeamsAndRepos, repositoriesToIgnore)))
    }
  }

  def reloadCache(fullRefreshWithHighApiCall:Option[Boolean]) = Action {
    dataReloadScheduler.reload(fullRefreshWithHighApiCall.getOrElse(false))
    Ok("Cache reload triggered successfully")
  }

  def clearCache() = Action.async {
    teamsAndReposPersister.clearAllData.map(r => Ok(s"Cache cleared successfully: $r"))
  }


  private def format(dateTime: LocalDateTime): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(dateTime, ZoneId.of("GMT")))
  }

  private def format(dateTime: Option[LocalDateTime]): String = {
    dateTime.fold("Not Available")(format)
  }

  private def determineServicesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]): JsValue =
    if (request.getQueryString("details").nonEmpty)
      Json.toJson(TeamRepositories.getRepositoryDetailsList(data, RepoType.Service, urlTemplatesProvider.ciUrlTemplates))
    else if (request.getQueryString("teamDetails").nonEmpty)
      Json.toJson(TeamRepositories.getRepositoryToTeamNameList(data))
    else Json.toJson(TeamRepositories.getAllRepositories(data).filter(_.repoType == RepoType.Service))

  private def determineLibrariesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]) = {
    if (request.getQueryString("details").nonEmpty)
      Json.toJson(TeamRepositories.getRepositoryDetailsList(data, RepoType.Library, urlTemplatesProvider.ciUrlTemplates))
    else
      Json.toJson(TeamRepositories.getAllRepositories(data).filter(_.repoType == RepoType.Library))
  }
}
