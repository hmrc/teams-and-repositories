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
import play.api.libs.json.{JsError, OFormat, Reads}
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.GitRepository
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IntegrationTestSupportController @Inject()(
  repositoriesPersistence: RepositoriesPersistence,
  jenkinsJobsPersistence : JenkinsJobsPersistence,
  cc                     : ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {
  private implicit val bjf: Reads[JenkinsJobsPersistence.Job] = JenkinsJobsPersistence.Job.mongoFormat
  private implicit val ghf: OFormat[GitRepository]            = GitRepository.apiFormat

  private def validateJson[A: Reads] =
    parse.json.validate(_.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e))))

  def addRepositories() = Action.async(validateJson[Seq[GitRepository]]){ implicit request =>
    repositoriesPersistence.updateRepos(request.body).map( _ => Ok("Ok"))
  }

  def clearAll() = Action.async {
    repositoriesPersistence.collection.deleteMany(Document()).toFuture().map(_ => Ok("Ok"))
  }

  def putJenkinsJobs() = Action.async(validateJson[Seq[JenkinsJobsPersistence.Job]]) { implicit request =>
    jenkinsJobsPersistence.putAll(request.body).map(_ => Ok("Done"))
  }

  def clearJenkins() = Action.async {
    jenkinsJobsPersistence.collection.deleteMany(Document()).toFuture().map(_ => Ok("Ok"))
  }
}
