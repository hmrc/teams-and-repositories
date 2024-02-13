/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.testonly

import org.mongodb.scala.bson.Document
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json, OFormat, Reads}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence, TeamSummaryPersistence}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IntegrationTestSupportController @Inject()(
  repositoriesPersistence: RepositoriesPersistence,
  teamSummaryPersistence: TeamSummaryPersistence,
  jenkinsJobsPersistence : JenkinsJobsPersistence,
  cc                     : ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {
  private implicit val bjf: Reads[JenkinsJobsPersistence.Job] = JenkinsJobsPersistence.Job.mongoFormat
  private implicit val ghf: OFormat[GitRepository]            = GitRepository.apiFormat

  private def validateJson[A: Reads]: BodyParser[A] =
    parse.json.validate(_.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e))))

  def addRepositories(): Action[Seq[GitRepository]] = Action.async(validateJson[Seq[GitRepository]]){ implicit request =>
    repositoriesPersistence.updateRepos(request.body).map( _ => Ok("Ok"))
  }

  def clearAll(): Action[AnyContent] = Action.async {
    repositoriesPersistence.collection.deleteMany(Document()).toFuture().map(_ => Ok("Ok"))
  }

  def putJenkinsJobs(): Action[Seq[JenkinsJobsPersistence.Job]] = Action.async(validateJson[Seq[JenkinsJobsPersistence.Job]]) { implicit request =>
    jenkinsJobsPersistence.putAll(request.body).map(_ => Ok("Done"))
  }

  def clearJenkins(): Action[AnyContent] = Action.async {
    jenkinsJobsPersistence.collection.deleteMany(Document()).toFuture().map(_ => Ok("Ok"))
  }

  def addTeamSummary(): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      implicit val tsFormat: OFormat[TeamSummary] =  TeamSummary.apiFormat
      request.body.validate[Seq[TeamSummary]] match {
        case JsSuccess(teams, _) => teamSummaryPersistence.addTeamSummaries(teams).map(_ => Ok("Ok"))
        case e: JsError => Future.successful(BadRequest(e.errors.mkString))
      }
  }

  def deleteAllTeamSummaries(): Action[AnyContent] = Action.async {
    teamSummaryPersistence.deleteAll().map(_ => Ok("Ok"))
  }
}
