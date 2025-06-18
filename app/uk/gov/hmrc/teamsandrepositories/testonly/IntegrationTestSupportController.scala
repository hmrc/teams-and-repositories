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

import org.mongodb.scala.bson.{BsonDocument, Document}
import play.api.libs.json.{Format, JsError, Reads}
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.model.{DeletedGitRepository, GitRepository, TeamSummary}
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, JenkinsJobsPersistence, RepositoriesPersistence, TeamSummaryPersistence}
import org.mongodb.scala.ObservableFuture

import java.time.Instant
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class IntegrationTestSupportController @Inject()(
  repositoriesPersistence       : RepositoriesPersistence,
  deletedRepositoriesPersistence: DeletedRepositoriesPersistence,
  teamSummaryPersistence        : TeamSummaryPersistence,
  jenkinsJobsPersistence        : JenkinsJobsPersistence,
  cc                            : ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  private def validateJson[A: Reads]: BodyParser[A] =
    parse.json.validate(_.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e))))

  def addRepositories(): Action[Seq[GitRepository]] =
    given Format[GitRepository] = GitRepository.apiFormat
    Action.async(validateJson[Seq[GitRepository]]): request =>
      repositoriesPersistence.putRepos(request.body, updateStartTime = Instant.now()).map(_ => Ok("Ok"))

  def clearAll: Action[AnyContent] =
    Action.async:
      repositoriesPersistence.collection.deleteMany(Document()).toFuture().map(_ => Ok("Ok"))

  def putDeletedRepositories: Action[Seq[DeletedGitRepository]] =
    given Format[DeletedGitRepository] = DeletedGitRepository.apiFormat
    Action.async(validateJson[Seq[DeletedGitRepository]]): request =>
      deletedRepositoriesPersistence.putAll(request.body).map(_ => Ok("Ok"))

  def putJenkinsJobs: Action[Seq[JenkinsJobsPersistence.Job]] =
    given Reads[JenkinsJobsPersistence.Job] = JenkinsJobsPersistence.Job.mongoFormat
    Action.async(validateJson[Seq[JenkinsJobsPersistence.Job]]): request =>
      jenkinsJobsPersistence.putAll(request.body).map(_ => Ok("Done"))

  def clearJenkins: Action[AnyContent] =
    Action.async:
      jenkinsJobsPersistence.collection.deleteMany(Document()).toFuture().map(_ => Ok("Ok"))

  def addTeamSummary(): Action[Seq[TeamSummary]] =
    given Format[TeamSummary] = TeamSummary.apiFormat
    Action.async(validateJson[Seq[TeamSummary]]): request =>
      teamSummaryPersistence.collection.insertMany(request.body).toFuture().map(_ => Ok("Ok"))

  def deleteAllTeamSummaries(): Action[AnyContent] =
    Action.async:
      teamSummaryPersistence.collection.deleteMany(BsonDocument()).toFuture().map(_ => Ok("Ok"))
