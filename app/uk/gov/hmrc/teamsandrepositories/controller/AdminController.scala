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

package uk.gov.hmrc.teamsandrepositories.controller

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.persistence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.schedulers.DataReloadScheduler

import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject()(
  dataReloadScheduler   : DataReloadScheduler,
  teamsAndReposPersister: TeamsAndReposPersister,
  cc                    : ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(cc) {

  def reloadCache() = Action {
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
}
