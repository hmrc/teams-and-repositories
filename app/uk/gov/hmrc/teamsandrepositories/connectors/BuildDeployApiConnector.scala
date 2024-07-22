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

package uk.gov.hmrc.teamsandrepositories.connectors

import play.api.Logging
import play.api.libs.ws.writeableOf_JsValue
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.BuildDeployApiConfig
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class BuildDeployApiConnector @Inject()(
  httpClientV2          : HttpClientV2,
  config                : BuildDeployApiConfig
)(using ExecutionContext
) extends Logging:

  import BuildDeployApiConnector._

  private given HeaderCarrier = HeaderCarrier()

  def getBuildJobsDetails(): Future[Seq[BuildDeployApiConnector.Detail]] =
    given Reads[Seq[Detail]] =
      Reads.at(__ \ "details")(Reads.seq(Detail.reads))

    val url =
      url"${config.baseUrl}/get-build-jobs"

    httpClientV2.post(url)
      .execute[Seq[Detail]]
      .recoverWith:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)

  def enableBranchProtection(repoName: String, statusChecks: List[JenkinsJobsPersistence.Job]): Future[Unit] =
    for
      res <- httpClientV2
              .post(url"${config.baseUrl}/set-branch-protection")
              .withBody(
                Json.toJson(BuildDeployApiConnector.BranchProtection(
                  repoName              = repoName
                , enable                = true
                , requireBranchUpToDate = false
                , statusChecks          = statusChecks.map(_.jobName)
                ))
              ).execute[BuildDeployApiConnector.ChangesBranchProtectionResult]
      _   <- if res.success then Future.unit
             else Future.failed(Throwable(s"Failed to set branch protection for $repoName: ${res.message}"))
    yield ()

object BuildDeployApiConnector:

  case class Detail(
   repoName : String,
   buildJobs: List[BuildJob]
  )

  case class BuildJob(
    jobName    : String,
    jenkinsUrl : String,
    jobType    : JobType,
  )

  object Detail:
    val reads: Reads[Detail] =
      given Reads[BuildJob] =
        ( (__ \ "name").read[String].map(_.split("/").last)
        ~ (__ \ "url" ).read[String]
        ~ (__ \ "type").read[JobType]
        )(BuildJob.apply _)

      ( (__ \ "repository_name").read[String]
      ~ (__ \ "build_jobs"     ).read[List[BuildJob]]
      )(Detail.apply _)

  enum JobType(val asString: String ):
    case Job      extends JobType("job"     )
    case Pipeline extends JobType("pipeline")

  object JobType:

    def parse(s: String): JobType =
      values
        .find(_.asString.equalsIgnoreCase(s))
        .getOrElse(Job)

    given Format[JobType] =
      Format.of[String].inmap(parse, _.asString)

  final case class BranchProtection(
    repoName             : String
  , enable               : Boolean
  , requireBranchUpToDate: Boolean
  , statusChecks         : List[String]
  )

  object BranchProtection:
    given Writes[BranchProtection] =
    ( (__ \ "repository_name"           ).write[String]
    ~ (__ \ "set_branch_protection_rule").write[Boolean]
    ~ (__ \ "require_branch_up_to_date" ).write[Boolean]
    ~ (__ \ "status_checks"             ).write[List[String]]
    )(b => Tuple.fromProductTyped(b))

  final case class ChangesBranchProtectionResult(
    success: Boolean
  , message: String
  )

  object ChangesBranchProtectionResult:
    given Reads[ChangesBranchProtectionResult] =
      ( (__ \ "success").read[Boolean]
      ~ (__ \ "message").read[String]
      ) (ChangesBranchProtectionResult.apply _)
