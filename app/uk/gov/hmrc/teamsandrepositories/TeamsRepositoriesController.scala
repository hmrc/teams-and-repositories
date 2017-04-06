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
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global


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

object RepositoryDetails {
  def buildRepositoryDetails(primaryRepository: Option[GitRepository],
                                     allRepositories: Seq[GitRepository],
                                     teamNames: Seq[String],
                                     urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    primaryRepository.map { repo =>

      val sameNameRepos: Seq[GitRepository] = allRepositories.filter(r => r.name == repo.name)
      val createdDate = sameNameRepos.minBy(_.createdDate).createdDate
      val lastActiveDate = sameNameRepos.maxBy(_.lastActiveDate).lastActiveDate

      val repoDetails = RepositoryDetails(
        repo.name,
        repo.description,
        createdDate,
        lastActiveDate,
        repo.repoType,
        teamNames,
        allRepositories.map { repo =>
          Link(
            githubName(repo.isInternal),
            githubDisplayName(repo.isInternal),
            repo.url)
        })

      val repositoryForCiUrls: GitRepository = allRepositories.find(!_.isInternal).fold(repo)(identity)

      if (hasEnvironment(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates), environments = buildEnvironmentUrls(repo, urlTemplates))
      else if (hasBuild(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates))
      else repoDetails
    }
  }

  private def githubName(isInternal: Boolean) = if (isInternal) "github-enterprise" else "github-com"

  private def githubDisplayName(isInternal: Boolean) = if (isInternal) "Github Enterprise" else "GitHub.com"

  private def hasEnvironment(repo: GitRepository): Boolean = repo.repoType == RepoType.Service

  private def hasBuild(repo: GitRepository): Boolean = repo.repoType == RepoType.Library

  private def buildEnvironmentUrls(repository: GitRepository, urlTemplates: UrlTemplates): Seq[Environment] = {
    urlTemplates.environments.map { case (name, tps) =>
      val links = tps.map { tp => Link(tp.name, tp.displayName, tp.url(repository.name)) }
      Environment(name, links)
    }.toSeq
  }

  private def buildCiUrls(repository: GitRepository, urlTemplates: UrlTemplates): List[Link] =
    repository.isInternal match {
      case true => buildUrls(repository, urlTemplates.ciClosed)
      case false => buildUrls(repository, urlTemplates.ciOpen)
    }

  private def buildUrls(repo: GitRepository, templates: Seq[UrlTemplate]) =
    templates.map(t => Link(t.name, t.displayName, t.url(repo.name))).toList

}

case class Repository(name: String, createdAt: Long, lastUpdatedAt: Long, repoType: RepoType.RepoType)

object Repository {
  implicit val repoDetailsFormat = Json.format[Repository]
}

case class Team(name: String,
                firstActiveDate: Option[Long] = None,
                lastActiveDate: Option[Long] = None,
                firstServiceCreationDate: Option[Long] = None,
                repos: Option[Map[RepoType.Value, Seq[String]]])

object Team {

  implicit val mapReads: Reads[Map[RepoType.RepoType, Seq[String]]] = new Reads[Map[RepoType.RepoType, Seq[String]]] {
    def reads(jv: JsValue): JsResult[Map[RepoType.RepoType, Seq[String]]] =
      JsSuccess(jv.as[Map[String, Seq[String]]].map { case (k, v) =>
        RepoType.withName(k) -> v.asInstanceOf[Seq[String]]
      })
  }

  implicit val mapWrites: Writes[Map[RepoType.RepoType, Seq[String]]] = new Writes[Map[RepoType.RepoType, Seq[String]]] {
    def writes(map: Map[RepoType.RepoType, Seq[String]]): JsValue =
      Json.obj(map.map { case (s, o) =>
        val ret: (String, JsValueWrapper) = s.toString -> JsArray(o.map(JsString))

        ret
      }.toSeq: _*)
  }

  implicit val mapFormat: Format[Map[RepoType.RepoType, Seq[String]]] = Format(mapReads, mapWrites)


  implicit val format = Json.format[Team]
}

object BlockingIOExecutionContext {
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))
}


@Singleton
class TeamsRepositoriesController @Inject()(dataReloadScheduler: DataReloadScheduler,
                                            teamsAndReposPersister: TeamsAndReposPersister,
                                            urlTemplatesProvider: UrlTemplatesProvider,
                                            configuration: Configuration,
                                            mongoTeamsAndReposPersister: TeamsAndReposPersister) extends BaseController {

  import Repository._

  import scala.collection.JavaConverters._

  val TimestampHeaderName = "X-Cache-Timestamp"

  val repositoriesToIgnore: List[String] = configuration.getStringList("shared.repositories").fold(List.empty[String])(_.asScala.toList)

  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]


  def repositoryDetails(name: String) = Action.async {
    val repoName = URLDecoder.decode(name, "UTF-8")

    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>
      TeamRepositories.findRepositoryDetails(allTeamsAndRepos, repoName, urlTemplatesProvider.ciUrlTemplates) match {
        case None =>
          NotFound
        case Some(x: RepositoryDetails) =>
          Ok(Json.toJson(x)).withHeaders(TimestampHeaderName -> format(timestamp))
      }
    }
  }


  def services() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>
      Ok(determineServicesResponse(request, allTeamsAndRepos))
        .withHeaders(TimestampHeaderName -> format(timestamp))
    }
  }

  def libraries() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>
      Ok(determineLibrariesResponse(request, allTeamsAndRepos))
        .withHeaders(TimestampHeaderName -> format(timestamp))
    }
  }


  def allRepositories() = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>
      Ok(Json.toJson(TeamRepositories.getAllRepositories(allTeamsAndRepos)))
        .withHeaders(TimestampHeaderName -> format(timestamp))
    }
  }

  def teams() = Action.async { implicit request =>
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>
      Results.Ok(Json.toJson(TeamRepositories.getTeamList(allTeamsAndRepos, repositoriesToIgnore)))
        .withHeaders(TimestampHeaderName -> format(timestamp))
    }
  }

  def repositoriesByTeam(teamName: String) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>

      (TeamRepositories.getTeamRepositoryNameList(allTeamsAndRepos, teamName) match {
        case None => NotFound
        case Some(x) => Results.Ok(Json.toJson(x.map { case (t, v) => (t.toString, v) }))
      }).withHeaders(TimestampHeaderName -> format(timestamp))
    }
  }


  def repositoriesWithDetailsByTeam(teamName: String) = Action.async {
    mongoTeamsAndReposPersister.getAllTeamAndRepos.map { case (allTeamsAndRepos, timestamp) =>

      (TeamRepositories.findTeam(allTeamsAndRepos, teamName, repositoriesToIgnore) match {
        case None => NotFound
        case Some(x) => Results.Ok(Json.toJson(x))
      }).withHeaders(TimestampHeaderName -> format(timestamp))
    }
  }

  def reloadCache() = Action {
    dataReloadScheduler.reload
    Ok("Cache reload triggered successfully")
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
