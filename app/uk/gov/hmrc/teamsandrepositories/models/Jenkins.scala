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

package uk.gov.hmrc.teamsandrepositories.models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class BuildData(
  number     : Int,
  url        : String,
  timestamp  : Instant,
  result     : Option[BuildResult],
  description: Option[String]
)
object BuildData {

  val mongoFormat: OFormat[BuildData] =
    ( (__ \ "number"     ).format[Int]
    ~ (__ \ "url"        ).format[String]
    ~ (__ \ "timestamp"  ).format(MongoJavatimeFormats.instantFormat)
    ~ (__ \ "result"     ).formatNullable[BuildResult]
    ~ (__ \ "description").formatNullable[String]
    )(apply, unlift(unapply))

  val apiWrites: Writes[BuildData] =
    ( (__ \ "number"     ).write[Int]
    ~ (__ \ "url"        ).write[String]
    ~ (__ \ "timestamp"  ).write[Instant]
    ~ (__ \ "result"     ).writeNullable[BuildResult]
    ~ (__ \ "description").writeNullable[String]
    )(unlift(unapply))

  val jenkinsReads: Reads[BuildData] =
    ( (__ \ "number"     ).read[Int]
    ~ (__ \ "url"        ).read[String]
    ~ (__ \ "timestamp"  ).read[Instant]
    ~ (__ \ "result"     ).readNullable[BuildResult]
    ~ (__ \ "description").readNullable[String]
    )(apply _)
}


case class JenkinsJobs(jobs: Seq[JenkinsObject.StandardJob])
object JenkinsJobs {
  implicit val reads: Reads[JenkinsJobs] = {
    implicit val x: Reads[JenkinsObject.StandardJob] = JenkinsObject.StandardJob.jenkinsReads
    Json.reads[JenkinsJobs]
  }
}

sealed trait JenkinsObject

object JenkinsObject {

  case class Folder(
    name      : String,
    jenkinsUrl: String,
    jobs      : Seq[JenkinsObject]
  ) extends JenkinsObject

  case class StandardJob(
    name       : String,
    jenkinsUrl : String,
    latestBuild: Option[BuildData],
    gitHubUrl  : Option[String]
  ) extends JenkinsObject

  case class PipelineJob(
    name      : String,
    jenkinsUrl: String
  ) extends JenkinsObject

  object StandardJob {

    val mongoFormat: Format[StandardJob] =
      ( (__ \ "name"       ).format[String]
      ~ (__ \ "jenkinsURL" ).format[String]
      ~ (__ \ "latestBuild").formatNullable(BuildData.mongoFormat)
      ~ (__ \ "gitHubUrl"  ).formatNullable[String]
      )(apply, unlift(unapply))

    val apiWrites: Writes[StandardJob] =
      ( (__ \ "name"       ).write[String]
      ~ (__ \ "jenkinsURL" ).write[String]
      ~ (__ \ "latestBuild").writeNullable(BuildData.apiWrites)
      ~ (__ \ "gitHubUrl"  ).writeNullable[String]
      )(unlift(unapply))

    private def extractGithubUrl = Reads[Option[String]] { js =>
      val l: List[JsValue] = (__ \ "scm" \ "userRemoteConfigs" \\ "url") (js)
      l.headOption match {
        case Some(v) => JsSuccess(Some(v.as[String].toLowerCase)) // github organisation can be uppercase
        case None    => JsSuccess(None)
      }
    }

    val jenkinsReads: Reads[StandardJob] =
      ( (__ \ "name"     ).read[String]
      ~ (__ \ "url"      ).read[String]
      ~ (__ \ "lastBuild").readNullable[BuildData](BuildData.jenkinsReads)
      ~ extractGithubUrl
      )(apply _)
  }

  private lazy val folderReads: Reads[Folder] =
    ( (__ \ "name").read[String]
    ~ (__ \ "url" ).read[String]
    ~ (__ \ "jobs").lazyRead(Reads.seq[JenkinsObject](jenkinsObjectReads))
    )(Folder.apply _)

  private val pipelineReads: Reads[PipelineJob] =
    ( (__ \ "name").read[String]
    ~ (__ \ "url" ).read[String]
    )(PipelineJob.apply _)

  implicit val jenkinsObjectReads: Reads[JenkinsObject] = json =>
    (json \ "_class").validate[String].flatMap {
      case "com.cloudbees.hudson.plugins.folder.Folder"     => folderReads.reads(json)
      case "hudson.model.FreeStyleProject"                  => StandardJob.jenkinsReads.reads(json)
      case "org.jenkinsci.plugins.workflow.job.WorkflowJob" => pipelineReads.reads(json)
      case value                                            => throw new Exception(s"Unsupported Jenkins class $value")
    }
}

object JenkinsObjects {
  implicit val jenkinsReads: Reads[Seq[JenkinsObject]] =
    (__ \ "jobs").read(Reads.seq[JenkinsObject])
}
