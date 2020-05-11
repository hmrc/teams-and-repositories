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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class JenkinsController @Inject()(jenkinsService: JenkinsService, cc: ControllerComponents)
  extends BackendController(cc) {

  private implicit val apiWriter: Writes[BuildJob] = BuildJob.apiWriter

  def lookup(service: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      findService <- jenkinsService.findByService(service)
      result      =  findService.map(links => Ok(Json.toJson(links))).getOrElse(NoContent)
    } yield result

  }
}