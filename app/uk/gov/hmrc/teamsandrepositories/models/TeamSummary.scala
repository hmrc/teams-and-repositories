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
  lastActiveDate: Option[Instant],
  repos         : Seq[String]
)

object TeamSummary {
  def apply(teamName: String, gitRepos: Seq[GitRepository]): TeamSummary =
    TeamSummary(
      name           = teamName,
      lastActiveDate = if (gitRepos.nonEmpty) Some(gitRepos.map(_.lastActiveDate).max) else None,
      repos          = gitRepos.map(_.name)
    )

  val apiFormat: OFormat[TeamSummary] =
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).format[Seq[String]]
    )(TeamSummary.apply, unlift(TeamSummary.unapply))

  val mongoFormat: OFormat[TeamSummary] =
    ( (__ \ "name"           ).format[String]
    ~ (__ \ "lastActiveDate" ).formatNullable[Instant](MongoJavatimeFormats.instantFormat)
    ~ (__ \ "repos"          ).format[Seq[String]]
    )(TeamSummary.apply, unlift(TeamSummary.unapply))
}
