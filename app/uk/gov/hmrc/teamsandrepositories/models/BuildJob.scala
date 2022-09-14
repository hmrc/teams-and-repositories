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
                      result: Option[BuildResult]
                    )
object BuildData {

  val mongoFormat: OFormat[BuildData] =
    ((__ \ "number").format[Int]
      ~ (__ \ "url").format[String]
      ~ (__ \ "timestamp").format(MongoJavatimeFormats.instantFormat)
      ~ (__ \ "result").formatNullable[BuildResult]
      ) (apply, unlift(unapply))

  val apiWrites: Writes[BuildData] =
    ((__ \ "number").write[Int]
      ~ (__ \ "url").write[String]
      ~ (__ \ "timestamp").write[Instant]
      ~ (__ \ "result").writeNullable[BuildResult]
      ) (unlift(unapply))

  val jenkinsReads: Reads[BuildData] =
    ((__ \ "number").read[Int]
      ~ (__ \ "url").read[String]
      ~ (__ \ "timestamp").read[Instant]
      ~ (__ \ "result").readNullable[BuildResult]
      ) (apply _)
}


sealed trait JenkinsObject

case class JenkinsFolder(service: String, jenkinsURL: String, objects: Seq[JenkinsObject]) extends JenkinsObject
case class BuildJob(service: String, jenkinsURL: String, latestBuild: Option[BuildData]) extends JenkinsObject

case class PipelineJob(service: String, jenkinsURL: String) extends JenkinsObject

object BuildJob {

  val mongoReadFormat: Reads[BuildJob] =
    ((__ \ "service").read[String]
      ~ (__ \ "jenkinsURL").read[String]
      ~ (__ \ "latestBuild").readNullable(BuildData.mongoFormat.reads)
      ) (apply _)

  val mongoWriteFormat: Writes[BuildJob] =
    ((__ \ "service").write[String]
      ~ (__ \ "jenkinsURL").write[String]
      ~ (__ \ "latestBuild").writeNullable(BuildData.mongoFormat.writes)
      ) (unlift(unapply))

  val mongoFormat: Format[BuildJob] = Format(mongoReadFormat, mongoWriteFormat)

  val apiWrites: Writes[BuildJob] =
    ((__ \ "service").write[String]
      ~ (__ \ "jenkinsURL").write[String]
      ~ (__ \ "latestBuild").writeNullable(BuildData.apiWrites)
      ) (unlift(unapply))

  val jenkinsBuildJobReads: Reads[BuildJob] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
      ~ (__ \ "lastBuild").readNullable[BuildData](BuildData.jenkinsReads)
    ) (apply _)
}

object JenkinsObject {

  private lazy val folderReads: Reads[JenkinsFolder] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
      ~ (__ \ "jobs").lazyRead(Reads.seq[JenkinsObject](jenkinsObjectReads))
    ) (JenkinsFolder)
  private val pipelineReads: Reads[PipelineJob] = (
    (__ \ "name").read[String]
      ~ (__ \ "url").read[String]
    ) (PipelineJob)

  implicit val jenkinsObjectReads: Reads[JenkinsObject] = json =>
    json \ "_class" match {
      case JsDefined(JsString("com.cloudbees.hudson.plugins.folder.Folder")) => folderReads.reads(json)
      case JsDefined(JsString("hudson.model.FreeStyleProject")) => BuildJob.jenkinsBuildJobReads.reads(json)
      case JsDefined(JsString("org.jenkinsci.plugins.workflow.job.WorkflowJob")) => pipelineReads.reads(json)
    }
}

case class JenkinsBuildJobsWrapper(jobs: Seq[JenkinsObject])

object JenkinsBuildJobsWrapper {
  implicit val jenkinsBuildJobsWrapperReads: Reads[JenkinsBuildJobsWrapper] =
    (__ \ "jobs").read(Reads.seq[JenkinsObject]).map(jobs => JenkinsBuildJobsWrapper(jobs))
}