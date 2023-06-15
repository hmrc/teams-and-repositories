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

import play.api.Logger
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

sealed trait BuildJobType { def asString: String }

object BuildJobType {
  case object Job         extends BuildJobType { override val asString = "job"         }
  case object Pipeline    extends BuildJobType { override val asString = "pipeline"    }
  case object Performance extends BuildJobType { override val asString = "performance" }

  private val logger = Logger(this.getClass)

  val values: List[BuildJobType] =
    List(Job, Pipeline, Performance)

  def parse(s: String): BuildJobType =
    values
      .find(_.asString.equalsIgnoreCase(s)).getOrElse {
        logger.info(s"Unable to find job type: $s, defaulted to: job")
        Job
    }

  implicit val format: Format[BuildJobType] =
    Format.of[String].inmap(parse, _.asString)
}

case class BuildJob(
  repoName   : String,
  jobName    : String,
  jenkinsUrl : String,
  jobType    : BuildJobType,
  latestBuild: Option[BuildData]
) {
  val gitHubUrl = s"https://github.com/hmrc/$repoName.git"
}

object BuildJob {
  def reads(repoName: String): Reads[BuildJob] =
    ( Reads.pure(repoName)
    ~ (__ \ "name"       ).read[String].map(_.split("/").last)
    ~ (__ \ "url"        ).read[String]
    ~ (__ \ "type"       ).read[BuildJobType]
    ~ Reads.pure(Option.empty[BuildData])
    )(BuildJob.apply _)


  val mongoFormat: Format[BuildJob] =
    ( (__ \ "repoName"   ).format[String]
    ~ (__ \ "jobName"    ).format[String]
    ~ (__ \ "jenkinsURL" ).format[String]
    ~ (__ \ "jobType"    ).format[BuildJobType]
    ~ (__ \ "latestBuild").formatNullable(BuildData.mongoFormat)
    )(apply, unlift(unapply))

  val apiWrites: Writes[BuildJob] =
    ( (__ \ "repoName"   ).write[String]
    ~ (__ \ "jobName"    ).write[String]
    ~ (__ \ "jenkinsURL" ).write[String]
    ~ (__ \ "jobType"    ).write[BuildJobType]
    ~ (__ \ "latestBuild").writeNullable(BuildData.apiWrites)
    )(unlift(unapply))
}