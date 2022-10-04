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

package uk.gov.hmrc.teamsandrepositories.models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class BuildData(
                      number: Int,
                      url: String,
                      timestamp: Instant,
                      result: Option[BuildResult],
                      description: Option[String]
                    )
object BuildData {

  val mongoFormat: OFormat[BuildData] =
    ((__ \ "number").format[Int]
      ~ (__ \ "url").format[String]
      ~ (__ \ "timestamp").format(MongoJavatimeFormats.instantFormat)
      ~ (__ \ "result").formatNullable[BuildResult]
      ~ (__ \ "description").formatNullable[String]
      ) (apply, unlift(unapply))

  val apiWrites: Writes[BuildData] =
    ((__ \ "number").write[Int]
      ~ (__ \ "url").write[String]
      ~ (__ \ "timestamp").write[Instant]
      ~ (__ \ "result").writeNullable[BuildResult]
      ~ (__ \ "description").writeNullable[String]
      ) (unlift(unapply))

  val jenkinsReads: Reads[BuildData] =
    ((__ \ "number").read[Int]
      ~ (__ \ "url").read[String]
      ~ (__ \ "timestamp").read[Instant]
      ~ (__ \ "result").readNullable[BuildResult]
      ~ (__ \ "description").readNullable[String]
      ) (apply _)
}


sealed trait JenkinsObject

object JenkinsObject {
  case class Folder(service: String, jenkinsURL: String, objects: Seq[JenkinsObject]) extends JenkinsObject
  case class BuildJob(service: String,
                      jenkinsURL: String,
                      latestBuild: Option[BuildData],
                      gitHubUrl: Option[String]) extends JenkinsObject

  case class PipelineJob(service: String, jenkinsURL: String) extends JenkinsObject

  object BuildJob {

    val mongoFormat: Format[BuildJob] =
      ((__ \ "service").format[String]
        ~ (__ \ "jenkinsURL").format[String]
        ~ (__ \ "latestBuild").formatNullable(BuildData.mongoFormat)
        ~ (__ \ "gitHubUrl").formatNullable[String]
        )(apply, unlift(unapply))

    val apiWrites: Writes[BuildJob] =
      ((__ \ "service").write[String]
        ~ (__ \ "jenkinsURL").write[String]
        ~ (__ \ "latestBuild").writeNullable(BuildData.apiWrites)
        ~ (__ \ "gitHubUrl").writeNullable[String]
        ) (unlift(unapply))

    private def extractGithubUrl = Reads[Option[String]] { js =>
      val l: List[JsValue] = (__ \ "scm" \ "userRemoteConfigs" \\ "url") (js)
      l.headOption match {
        case Some(value) => JsSuccess(Some(value.as[String]))
        case None => JsSuccess(None)
      }
    }

    val jenkinsBuildJobReads: Reads[BuildJob] = (
      (__ \ "name").read[String]
        ~ (__ \ "url").read[String]
        ~ (__ \ "lastBuild").readNullable[BuildData](BuildData.jenkinsReads)
        ~ extractGithubUrl
      ) (apply _)
  }

  private lazy val folderReads: Reads[Folder] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
      ~ (__ \ "jobs").lazyRead(Reads.seq[JenkinsObject](jenkinsObjectReads))
    ) (Folder)
  private val pipelineReads: Reads[PipelineJob] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
    ) (PipelineJob)

  implicit val jenkinsObjectReads: Reads[JenkinsObject] = json =>
    (json \ "_class").validate[String] flatMap {
      case "com.cloudbees.hudson.plugins.folder.Folder" => folderReads.reads(json)
      case "hudson.model.FreeStyleProject" => BuildJob.jenkinsBuildJobReads.reads(json)
      case "org.jenkinsci.plugins.workflow.job.WorkflowJob" => pipelineReads.reads(json)
      case value => throw new Exception(s"Unsupported Jenkins class $value")
    }
}

case class JenkinsObjects(objects: Seq[JenkinsObject])

object JenkinsObjects {
  implicit val jenkinsObjectsReads: Reads[JenkinsObjects] =
    (__ \ "jobs").read(Reads.seq[JenkinsObject]).map(obj => JenkinsObjects(obj))
}

case class BuildJobs(jobs: Seq[JenkinsObject.BuildJob])

object BuildJobs {
  private implicit val x: Writes[JenkinsObject.BuildJob] = JenkinsObject.BuildJob.apiWrites
  val apiWrites: OWrites[BuildJobs] = Json.writes[BuildJobs]
}