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

package uk.gov.hmrc.teamsandrepositories.controller.v2

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Predicate, Resource}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.model.{GitRepository, Organisation, RepoType, ServiceType, Tag, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence, TeamSummaryPersistence}
import uk.gov.hmrc.teamsandrepositories.service.BranchProtectionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsAndRepositoriesController @Inject()(
  repositoriesPersistence       : RepositoriesPersistence,
  teamSummaryPersistence        : TeamSummaryPersistence,
  deletedRepositoriesPersistence: DeletedRepositoriesPersistence,
  branchProtectionService       : BranchProtectionService,
  auth                          : BackendAuthComponents,
  cc                            : ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  given Format[GitRepository] = GitRepository.apiFormat
  given Format[TeamSummary]   = TeamSummary.apiFormat

  def repositories(
    name               : Option[String],
    organisation       : Option[Organisation],
    team               : Option[String],
    owningTeam         : Option[String],
    digitalServiceName : Option[String],
    archived           : Option[Boolean],
    repoType           : Option[RepoType],
    serviceType        : Option[ServiceType],
    tags               : Option[List[Tag]],
  ): Action[AnyContent] =
    Action.async: request =>
      repositoriesPersistence
        .find(name, organisation, team, owningTeam, digitalServiceName, archived, repoType, serviceType, tags)
        .map(result => Ok(Json.toJson(result.sortBy(_.name))))

  def teams(name: Option[String]): Action[AnyContent] =
    Action.async: request =>
      teamSummaryPersistence
        .findTeamSummaries(name)
        .map(result => Ok(Json.toJson(result)))

  def digitalServices: Action[AnyContent] =
    Action.async: request =>
      repositoriesPersistence.getDigitalServiceNames
        .map(result => Ok(Json.toJson(result)))

  def findRepo(repoName:String): Action[AnyContent] =
    Action.async: request =>
      repositoriesPersistence.findRepo(repoName).map:
        case None       => NotFound
        case Some(repo) => Ok(Json.toJson(repo))

  def enableBranchProtection(repoName: String): Action[JsValue] =
    auth
      .authorizedAction(
        Predicate.Permission(
          Resource.from("catalogue-repository", s"$repoName"),
          IAAction("WRITE_BRANCH_PROTECTION")
        )
      )
      .async[JsValue](parse.json): request =>
        val payload =
          implicitly[Reads[Boolean]].reads(request.body)

        payload.fold(
          errors  => Future.successful(BadRequest(Json.stringify(JsError.toJson(errors)))),
          enabled =>
            if enabled then
              branchProtectionService
                .enableBranchProtection(repoName)
                .map(_ => Ok)
            else
              Future.successful(BadRequest("Disabling branch protection is not currently supported.")))

  def decommissionedRepos(
    organisation: Option[Organisation]
  , repoType    : Option[RepoType]
  ): Action[AnyContent] =
    Action.async: request =>
      for
        archivedRepos  <- repositoriesPersistence
                            .find(isArchived = Some(true), organisation = organisation, repoType = repoType)
                            .map(_.map(repo => DecommissionedRepo(repo.name, Some(repo.repoType))))
        deletedRepos   <- deletedRepositoriesPersistence
                            .find(organisation = organisation, repoType = repoType)
                            .map(_.map(repo => DecommissionedRepo(repo.name, repo.repoType)))
        decommissioned =  (archivedRepos ++ deletedRepos)
                            .distinct
                            .sortBy(_.repoName.toLowerCase)
      yield Ok(Json.toJson(decommissioned))

case class DecommissionedRepo(
  repoName: String,
  repoType: Option[RepoType]
)

object DecommissionedRepo:
  given Writes[DecommissionedRepo] =
    ( (__ \ "repoName").write[String]
    ~ (__ \ "repoType").writeNullable[RepoType](RepoType.format)
    )(d => Tuple.fromProductTyped(d))
