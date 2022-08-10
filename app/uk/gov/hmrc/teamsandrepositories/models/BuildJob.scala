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

case class BuildJob(
  service   : String,
  jenkinsURL: String,
  builds:     Seq[BuildJobBuildData]
)

object BuildJob {

  val mongoReadFormat: Reads[BuildJob] =
    ((__ \ "service").read[String]
      ~ (__ \ "jenkinsURL").read[String]
      ~ (__ \ "builds").read(Reads.seq(BuildJobBuildData.mongoFormat.reads))
      ) (apply _)

  val mongoWriteFormat: Writes[BuildJob] =
    ((__ \ "service").write[String]
      ~ (__ \ "jenkinsURL").write[String]
      ~ (__ \ "builds").write(Writes.seq(BuildJobBuildData.mongoFormat.writes))
      ) (unlift(unapply))

  val mongoFormat: Format[BuildJob] = Format(mongoReadFormat, mongoWriteFormat)

  val apiWrites: Writes[BuildJob] =
    ( (__ \ "service"   ).write[String]
    ~ (__ \ "jenkinsURL").write[String]
    ~ (__ \ "builds").write(Writes.seq(BuildJobBuildData.apiWrites))
    )(unlift(unapply))
}

case class BuildJobBuildData(
                      number: Int,
                      url: String,
                      timestamp: Instant,
                      result: Option[String]
                    )
object BuildJobBuildData {

  val mongoFormat: OFormat[BuildJobBuildData] =
    ((__ \ "number").format[Int]
      ~ (__ \ "url").format[String]
      ~ (__ \ "timestamp").format(MongoJavatimeFormats.instantFormat)
      ~ (__ \ "result").formatNullable[String]
      ) (apply, unlift(unapply))

  val apiWrites: Writes[BuildJobBuildData] =
    ((__ \ "number").write[Int]
      ~ (__ \ "url").write[String]
      ~ (__ \ "timestamp").write[Instant]
      ~ (__ \ "result").writeNullable[String]
      ) (unlift(unapply))
}