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

package uk.gov.hmrc.teamsandrepositories.model

import play.api.libs.json.{Format, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class TeamSummary(
  name          : String,
  lastActiveDate: Option[Instant],
  repos         : Seq[String],
  lastUpdated   : Instant = Instant.now()
)

object TeamSummary:
  def apply(teamName: String, gitRepos: Seq[GitRepository], lastUpdated: Instant): TeamSummary =
    TeamSummary(
      name           = teamName,
      lastActiveDate = if gitRepos.nonEmpty then Some(gitRepos.map(_.lastActiveDate).max) else None,
      repos          = gitRepos.map(_.name),
      lastUpdated    = lastUpdated
    )

  val apiFormat: Format[TeamSummary] =
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).format[Seq[String]]
    )((name, date, repos) => TeamSummary(name, date, repos, Instant.now()),
      t => (t.name, t.lastActiveDate, t.repos)
    )

  val mongoFormat: Format[TeamSummary] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    ( (__ \ "name"           ).format[String]
    ~ (__ \ "lastActiveDate" ).formatNullable[Instant]
    ~ (__ \ "repos"          ).format[Seq[String]]
    ~ (__ \ "lastUpdated"    ).format[Instant]
    )(TeamSummary.apply, t => Tuple.fromProductTyped(t))
