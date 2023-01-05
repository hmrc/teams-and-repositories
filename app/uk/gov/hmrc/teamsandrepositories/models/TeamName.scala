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

import play.api.libs.json.{OFormat, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class TeamSummary(
  name          : String,
  createdDate   : Instant,
  lastActiveDate: Instant,
  repos         : Int
)

object TeamSummary {

  val apiFormat: OFormat[TeamSummary] = {
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "createdDate"   ).format[Instant]
    ~ (__ \ "lastActiveDate").format[Instant]
    ~ (__ \ "repos"         ).format[Int]
    )(TeamSummary.apply, unlift(TeamSummary.unapply))
  }

  val mongoFormat: OFormat[TeamSummary] = {
    implicit val inf = MongoJavatimeFormats.instantFormat
    ( (__ \ "_id"          ).format[String]
    ~ (__ \ "createdDate"   ).format[Instant]
    ~ (__ \ "lastActiveDate").format[Instant]
    ~ (__ \ "repos"         ).format[Int]
    )(TeamSummary.apply, unlift(TeamSummary.unapply))
  }
}
