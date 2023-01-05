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

package uk.gov.hmrc.teamsandrepositories.controller

import play.api.libs.json.{Json, OWrites, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models.BuildJobs
import uk.gov.hmrc.teamsandrepositories.models.JenkinsObject.BuildJob
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class JenkinsController @Inject()(
  jenkinsService: JenkinsService,
  cc            : ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(cc) {

  private implicit val bjw: Writes[BuildJob] = BuildJob.apiWrites
  private implicit val bjws: OWrites[BuildJobs] = BuildJobs.apiWrites

  def lookup(name: String): Action[AnyContent] = Action.async {
    for {
      findJob <- jenkinsService.findByJobName(name)
      result  =  findJob.map(links => Ok(Json.toJson(links))).getOrElse(NotFound)
    } yield result
  }

  def findAllJobsByRepo(name: String): Action[AnyContent] = Action.async {
    jenkinsService.findAllByRepo(name).map(result => Ok(Json.toJson(result)))
  }
}
