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

import play.api.libs.json.{Json, OFormat, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class TeamName (name: String, createdDate: Instant, lastActiveDate: Instant, repos: Int)

object TeamName {

  val apiFormat: OFormat[TeamName] = {
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "createdDate"   ).format[Instant]
    ~ (__ \ "lastActiveDate").format[Instant]
    ~ (__ \ "repos"         ).format[Int]
    )(TeamName.apply, unlift(TeamName.unapply))
  }

  val mongoFormat: OFormat[TeamName] = {
    implicit val inf = MongoJavatimeFormats.instantFormat
    ( (__ \ "_id"          ).format[String]
    ~ (__ \ "createdDate"   ).format[Instant]
    ~ (__ \ "lastActiveDate").format[Instant]
    ~ (__ \ "repos"         ).format[Int]
    )(TeamName.apply, unlift(TeamName.unapply))
  }
}
