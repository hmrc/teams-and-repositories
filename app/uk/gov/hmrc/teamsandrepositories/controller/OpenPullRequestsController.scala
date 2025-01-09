/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.libs.json.*
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.model.OpenPullRequest
import uk.gov.hmrc.teamsandrepositories.persistence.OpenPullRequestPersistence

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class OpenPullRequestsController @Inject()(
  openPullRequestPersistence: OpenPullRequestPersistence,
  cc: ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  private given OFormat[OpenPullRequest] = OpenPullRequest.mongoFormat

  def getOpenPrsByRepo(repoName: String): Action[AnyContent] = Action.async {
    openPullRequestPersistence
      .findOpenPullRequestsByRepo(repoName)
      .map(result => Ok(Json.toJson(result)))
  }

  def getOpenPrsByAuthor(author: String): Action[AnyContent] = Action.async {
    openPullRequestPersistence
      .findOpenPullRequestsByAuthor(author)
      .map(result => Ok(Json.toJson(result)))
  }
