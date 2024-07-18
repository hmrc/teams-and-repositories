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
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class JenkinsController @Inject()(
  jenkinsJobsPersistence: JenkinsJobsPersistence,
  cc                    : ControllerComponents
)(implicit ec: ExecutionContext
) extends BackendController(cc) {

  private implicit val jjw: Writes[JenkinsJobsPersistence.Job] = JenkinsController.apiJobWrites

  def lookup(name: String): Action[AnyContent] = Action.async {
    jenkinsJobsPersistence.findByJobName(name).map {
      case Some(jobs) => Ok(Json.toJson(jobs))
      case None       => NotFound
    }
  }

  def findAllJobsByRepo(name: String): Action[AnyContent] = Action.async {
    jenkinsJobsPersistence
      .findAllByRepo(name)
      .map(jobs => Ok(Json.obj("jobs" -> Json.toJson(jobs))))
  }
}

object JenkinsController {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
  import uk.gov.hmrc.teamsandrepositories.models.RepoType
  import java.time.Instant

  val apiJobWrites: Writes[JenkinsJobsPersistence.Job] = {
    implicit val latestBuildWrites: Writes[JenkinsConnector.LatestBuild] =
      ( (__ \ "number"     ).write[Int]
      ~ (__ \ "url"        ).write[String]
      ~ (__ \ "timestamp"  ).write[Instant]
      ~ (__ \ "result"     ).writeNullable[JenkinsConnector.LatestBuild.BuildResult]
      ~ (__ \ "description").writeNullable[String]
      )(l => Tuple.fromProductTyped(l))

    ( (__ \ "repoName"   ).write[String]
    ~ (__ \ "jobName"    ).write[String]
    ~ (__ \ "jenkinsURL" ).write[String]
    ~ (__ \ "jobType"    ).write[JenkinsJobsPersistence.JobType](JenkinsJobsPersistence.JobType.format)
    ~ (__ \ "repoType"   ).writeNullable[RepoType](RepoType.format)
    ~ (__ \ "latestBuild").writeNullable[JenkinsConnector.LatestBuild]
    )(j => Tuple.fromProductTyped(j))
  }
}
