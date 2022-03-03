/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.libs.json.{JsError, OFormat, Reads}
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.{BuildJob, GitRepository, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persistence.{BuildJobRepo, RepositoriesPersistence}

import scala.concurrent.{ExecutionContext, Future}

class IntegrationTestSupportController @Inject()(
                                                  teamsRepo    : RepositoriesPersistence,
                                                  jenkinsRepo  : BuildJobRepo,
                                                  futureHelpers: FutureHelpers,
                                                  cc           : ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(cc) {

  private implicit val trf: Reads[TeamRepositories] = TeamRepositories.apiFormat
  private implicit val bjf: Reads[BuildJob]         = BuildJob.mongoFormat
  private implicit val ghf: OFormat[GitRepository]  = GitRepository.apiFormat

  private def validateJson[A: Reads] =
    parse.json.validate(_.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e))))

  def addRepositories() = Action.async(validateJson[Seq[GitRepository]]){ implicit request =>
    teamsRepo.updateRepos(request.body).map( _ => Ok("Ok"))
  }

  def clearAll() = Action.async {
    teamsRepo.clearAllData.map(_ => Ok("Ok"))
  }

  def addJenkinsLinks() = Action.async(validateJson[Seq[BuildJob]]) { implicit request =>
    jenkinsRepo.update(request.body).map(_ => Ok("Done"))
  }

  def clearJenkins() = Action.async {
    jenkinsRepo.clearAllData.map(_ => Ok("Ok"))
  }
}
