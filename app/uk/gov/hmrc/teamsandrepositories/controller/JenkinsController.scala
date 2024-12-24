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

import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsController @Inject()(
  jenkinsJobsPersistence : JenkinsJobsPersistence,
  repositoriesPersistence: RepositoriesPersistence,
  cc                     : ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  private given Writes[JenkinsJobsPersistence.Job] = JenkinsController.apiJobWrites

  def lookup(name: String): Action[AnyContent] =
    Action.async:
      jenkinsJobsPersistence.findByJobName(name).map:
        case Some(jobs) => Ok(Json.toJson(jobs))
        case None       => NotFound

  def findAllJobsByRepo(name: String): Action[AnyContent] =
    Action.async:
      jenkinsJobsPersistence
        .findAllByRepo(name)
        .map(jobs => Ok(Json.obj("jobs" -> Json.toJson(jobs))))


  def findTestJobs(teamName: Option[String], digitalService: Option[String]): Action[AnyContent] =
    Action.async:
      for
        repos    <- (teamName, digitalService) match
                      case (None, None) => Future.successful(None)
                      case _            => repositoriesPersistence.find(
                                             owningTeam         = teamName,
                                             digitalServiceName = digitalService
                                           )
                                           .map(repos => Some(repos.map(_.name)))
        testJobs <- jenkinsJobsPersistence.findAll(repos)
      yield
        Ok(Json.toJson(testJobs))

object JenkinsController:
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
  import uk.gov.hmrc.teamsandrepositories.models.{RepoType, TestType}
  import java.time.Instant

  val apiJobWrites: Writes[JenkinsJobsPersistence.Job] =
    ( (__ \ "repoName"   ).write[String]
    ~ (__ \ "jobName"    ).write[String]
    ~ (__ \ "jenkinsURL" ).write[String]
    ~ (__ \ "jobType"    ).write[JenkinsJobsPersistence.JobType](JenkinsJobsPersistence.JobType.format)
    ~ (__ \ "repoType"   ).writeNullable[RepoType](RepoType.format)
    ~ (__ \ "testType"   ).writeNullable[TestType](TestType.format)
    ~ (__ \ "latestBuild").writeNullable[JenkinsConnector.LatestBuild](JenkinsConnector.LatestBuild.apiWrites)
    )(j => Tuple.fromProductTyped(j))
