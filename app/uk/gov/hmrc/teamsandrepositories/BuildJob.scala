/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class BuildJob(
  service   : String,
  jenkinsURL: String
)

object BuildJob {
  val mongoFormat: OFormat[BuildJob] =
    ( (__ \ "service"   ).format[String]
    ~ (__ \ "jenkinsURL").format[String]
    )(apply, unlift(unapply))

  val apiWrites: Writes[BuildJob] =
    ( (__ \ "service"   ).write[String]
    ~ (__ \ "jenkinsURL").write[String]
    )(unlift(unapply))
}
