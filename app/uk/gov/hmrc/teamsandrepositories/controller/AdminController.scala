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

import com.google.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.schedulers.DataReloadScheduler
import uk.gov.hmrc.teamsandrepositories.service.PersistingService

import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject()(
  dataReloadScheduler     : DataReloadScheduler,
  persistingService       : PersistingService,
  cc                      : ControllerComponents
)(using ExecutionContext) extends BackendController(cc):

  def reloadCache: Action[AnyContent] =
    Action:
      dataReloadScheduler.reload
      Ok("Cache reload triggered successfully")

  def reloadCacheForService(serviceName: String): Action[AnyContent] =
    Action.async:
      persistingService.updateRepository(serviceName).value.map:
        case Right(_)    => Ok(s"Cache reload for $serviceName triggered successfully")
        case Left(error) => InternalServerError(s"Failed to reload service $serviceName with error $error")
