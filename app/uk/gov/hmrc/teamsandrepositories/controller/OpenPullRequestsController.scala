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
import uk.gov.hmrc.teamsandrepositories.connector.UserManagementConnector
import uk.gov.hmrc.teamsandrepositories.model.OpenPullRequest
import uk.gov.hmrc.teamsandrepositories.persistence.{OpenPullRequestPersistence, RepositoriesPersistence, TeamSummaryPersistence}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OpenPullRequestsController @Inject()(
  userManagementConnector   : UserManagementConnector,
  teamSummaryPersistence    : TeamSummaryPersistence,
  repositoriesPersistence   : RepositoriesPersistence,
  openPullRequestPersistence: OpenPullRequestPersistence,
  cc                        : ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  private given Writes[OpenPullRequest] = OpenPullRequest.apiWrites

  def getOpenPrs(reposOwnedByTeamName: Option[String], reposOwnedByDigitalServiceName: Option[String], raisedByMembersOfTeamName: Option[String]): Action[AnyContent] =
    Action.async:
      (reposOwnedByTeamName, reposOwnedByDigitalServiceName, raisedByMembersOfTeamName) match
        case (Some(teamName), None, None) =>
          for
            teamSummaries <- teamSummaryPersistence.findTeamSummaries(Some(teamName))
            repos         =  teamSummaries.flatMap(_.repos)
            openPrs       <- openPullRequestPersistence.findOpenPullRequests(repos = Some(repos))
          yield Ok(Json.toJson(openPrs))
        case (None, Some(digitalServiceName), None) =>
          for
            repos   <- repositoriesPersistence.find(
                         name               = None,
                         team               = None,
                         owningTeam         = None,
                         digitalServiceName = Some(digitalServiceName),
                         isArchived         = None,
                         repoType           = None,
                         serviceType        = None,
                         tags               = None
                       )
            openPrs <- openPullRequestPersistence.findOpenPullRequests(repos = Some(repos.map(_.name)))
          yield Ok(Json.toJson(openPrs))
        case (None, None, Some(teamName)) =>
          for
            users           <- userManagementConnector.getUsersForTeam(teamName)
            githubUsernames = users.flatMap(_.githubUsername)
            openPrs         <- openPullRequestPersistence.findOpenPullRequests(authors = Some(githubUsernames))
          yield Ok(Json.toJson(openPrs))
        case _ =>
          Future.successful(BadRequest(Json.obj("error" -> "Provide only one of reposOwnedByTeamName, reposOwnedByDigitalServiceName, or raisedByMembersOfTeam")))
