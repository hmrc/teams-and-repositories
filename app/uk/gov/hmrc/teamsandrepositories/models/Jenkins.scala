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

case class BuildJob(
  name       : String,
  jenkinsUrl : String,
  jobType    : Option[String],
  latestBuild: Option[BuildData],
  gitHubUrl  : Option[String]
)

object BuildJob {
  val reads: Reads[BuildJob] =
    ( (__ \ "name"       ).read[String]
    ~ (__ \ "url"        ).read[String]
    ~ (__ \ "type"       ).readNullable[String]
    ~ Reads.pure(Option.empty[BuildData])
    ~ Reads.pure(Option.empty[String])
    )(BuildJob.apply _)

  val mongoFormat: Format[BuildJob] =
    ( (__ \ "name"       ).format[String]
    ~ (__ \ "jenkinsURL" ).format[String]
    ~ (__ \ "jobType"    ).formatNullable[String]
    ~ (__ \ "latestBuild").formatNullable(BuildData.mongoFormat)
    ~ (__ \ "gitHubUrl"  ).formatNullable[String]
    )(apply, unlift(unapply))

  val apiWrites: Writes[BuildJob] =
    ( (__ \ "name"       ).write[String]
    ~ (__ \ "jenkinsURL" ).write[String]
    ~ (__ \ "jobType"    ).formatNullable[String]
    ~ (__ \ "latestBuild").writeNullable(BuildData.apiWrites)
    ~ (__ \ "gitHubUrl"  ).writeNullable[String]
    )(unlift(unapply))
}