/*
 * Copyright 2020 HM Revenue & Customs
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

import java.net.URLDecoder

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplatesProvider
import uk.gov.hmrc.teamsandrepositories.controller.model.{Environment, Link, RepositoryDetails}
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories.DigitalService
import uk.gov.hmrc.teamsandrepositories.{DataReloadScheduler, RepoType}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsRepositoriesController @Inject()(
  dataReloadScheduler: DataReloadScheduler,
  teamsAndReposPersister: TeamsAndReposPersister,
  urlTemplatesProvider: UrlTemplatesProvider,
  configuration: Configuration,
  mongoTeamsAndReposPersister: TeamsAndReposPersister,
  controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(controllerComponents) {


  val TimestampHeaderName = "X-Cache-Timestamp"

  lazy val repositoriesToIgnore: List[String] =
    configuration.getOptional[Seq[String]]("shared.repositories").map(_.toList).getOrElse(List.empty[String])

  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats        = Json.format[Environment]
  implicit val serviceFormats     = Json.format[RepositoryDetails]

  def repositoryDetails(name: String) = Action.async {
    val repoName = URLDecoder.decode(name, "UTF-8")

    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      TeamRepositories.findRepositoryDetails(allTeamsAndRepos, repoName, urlTemplatesProvider.ciUrlTemplates) match {
        case None =>
          NotFound
        case Some(x: RepositoryDetails) =>
          Ok(toJson(x))
      }
    }
  }

  def digitalServiceDetails(digitalServiceName: String) = Action.async {
    val sanitisedDigitalServiceName = URLDecoder.decode(digitalServiceName, "UTF-8")

    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      TeamRepositories.findDigitalServiceDetails(allTeamsAndRepos, sanitisedDigitalServiceName) match {
        case None =>
          NotFound
        case Some(x: DigitalService) =>
          Ok(toJson(x))
      }
    }
  }

  def allServices = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None) map { allTeamsAndRepos =>
      Ok(determineServicesResponse(request, allTeamsAndRepos))
    }
  }

  def services = Action.async(parse.json) { implicit request =>
    withJsonBody[Set[String]] {
      case serviceNames if serviceNames.isEmpty =>
        Future.successful(Ok(determineServicesResponse(request, Nil)))
      case serviceNames =>
        mongoTeamsAndReposPersister.getTeamsAndRepos(serviceNames.toSeq) map { teamsAndRepos =>
          Ok(determineServicesResponse(request, teamsAndRepos))
        }
    }
  }

  def libraries = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      Ok(determineLibrariesResponse(request, allTeamsAndRepos))
    }
  }

  def digitalServices = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      val digitalServices: Seq[String] =
        allTeamsAndRepos
          .flatMap(_.repositories)
          .flatMap(_.digitalServiceName)
          .distinct
          .sorted

      Ok(toJson(digitalServices))
    }
  }

  def all = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map(allRecords => Ok(toJson(allRecords)))
  }

  def allRepositories(archived: Option[Boolean]) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.getAllRepositories(allTeamsAndRepos)))
    }
  }

  def teams = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.getTeamList(allTeamsAndRepos, repositoriesToIgnore)))
    }
  }

  def repositoriesByTeam(teamName: String) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      TeamRepositories.getTeamRepositoryNameList(allTeamsAndRepos, teamName) match {
        case None    => NotFound
        case Some(x) => Ok(toJson(x.map { case (t, v) => (t.toString, v) }))
      }
    }
  }

  def repositoriesWithDetailsByTeam(teamName: String) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      TeamRepositories.findTeam(allTeamsAndRepos, teamName, repositoriesToIgnore) match {
        case None    => NotFound
        case Some(x) => Ok(toJson(x))
      }
    }
  }

  def allTeamsAndRepositories = Action.async {
    mongoTeamsAndReposPersister.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.allTeamsAndTheirRepositories(allTeamsAndRepos, repositoriesToIgnore)))
    }
  }

  def reloadCache(fullRefreshWithHighApiCall: Option[Boolean]) = Action {
    dataReloadScheduler.reload
    Ok("Cache reload triggered successfully")
  }

  def clearCache() = Action.async {
    teamsAndReposPersister.clearAllData.map(r => Ok(s"Cache cleared successfully: $r"))
  }

  def resetLastActiveDate(repoName: String) = Action.async {
    teamsAndReposPersister
      .resetLastActiveDate(repoName)
      .map {
        case Some(modified) => Ok(Json.obj("message" -> s"'$repoName' last active date reset for 1 team(s)"))
        case None           => NotFound
      }
  }

  private def determineServicesResponse[A](request: Request[A], data: Seq[TeamRepositories]): JsValue =
    if (request.getQueryString("details").nonEmpty)
      toJson(TeamRepositories.getRepositoryDetailsList(data, RepoType.Service, urlTemplatesProvider.ciUrlTemplates))
    else if (request.getQueryString("teamDetails").nonEmpty)
      toJson(TeamRepositories.getRepositoryToTeamNameList(data))
    else
      toJson(TeamRepositories.getAllRepositories(data).filter(_.repoType == RepoType.Service))

  private def determineLibrariesResponse(request: Request[AnyContent], data: Seq[TeamRepositories]) =
    if (request.getQueryString("details").nonEmpty)
      toJson(TeamRepositories.getRepositoryDetailsList(data, RepoType.Library, urlTemplatesProvider.ciUrlTemplates))
    else
      toJson(TeamRepositories.getAllRepositories(data).filter(_.repoType == RepoType.Library))
}
