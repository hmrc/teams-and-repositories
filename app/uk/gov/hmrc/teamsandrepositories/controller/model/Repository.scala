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

package uk.gov.hmrc.teamsandrepositories.controller.model

import java.time.Instant
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType}

case class Repository(
  name         : String,
  teamNames    : Seq[String],
  createdAt    : Instant,
  lastUpdatedAt: Instant,
  repoType     : RepoType,
  language     : Option[String],
  isArchived   : Boolean,
  defaultBranch: String
)

object Repository {

  def create(gr: GitRepository, teamNames: Seq[String]): Repository =
    Repository(
      name          = gr.name,
      teamNames     = teamNames,
      createdAt     = gr.createdDate,
      lastUpdatedAt = gr.lastActiveDate,
      repoType      = gr.repoType,
      language      = gr.language,
      isArchived    = gr.isArchived,
      defaultBranch = gr.defaultBranch
    )

  implicit val format: OFormat[Repository] = {
    implicit val rtf: Format[RepoType] = RepoType.format
    ( (__ \ "name"         ).format[String]
    ~ (__ \ "teamNames"    ).format[Seq[String]]
    ~ (__ \ "createdAt"    ).format[Instant]
    ~ (__ \ "lastUpdatedAt").format[Instant]
    ~ (__ \ "repoType"     ).format[RepoType]
    ~ (__ \ "language"     ).formatNullable[String]
    ~ (__ \ "isArchived"   ).format[Boolean]
    ~ (__ \ "defaultBranch").format[String]
    )(apply, unlift(unapply))
  }
}

case class Team(
  name           : String,
  createdDate    : Option[Instant]                    = None,
  lastActiveDate : Option[Instant]                    = None,
  repos          : Option[Map[RepoType, List[String]]],
  ownedRepos     : Seq[String]                        = Nil
)

object Team {

  val mapFormat: Format[Map[RepoType, List[String]]] = {
    val mapReads: Reads[Map[RepoType, List[String]]] = jv => JsSuccess(
      jv.as[Map[String, List[String]]].map {
        case (k, v) =>
          RepoType.parse(k).getOrElse(throw new NoSuchElementException()) -> v
      }
    )

    val mapWrites: Writes[Map[RepoType, List[String]]] = (map: Map[RepoType, List[String]]) => Json.obj(map.map {
        case (s, o) =>
          val ret: (String, JsValueWrapper) = s.toString -> JsArray(o.map(JsString.apply))
          ret
      }.toSeq: _*)
    Format(mapReads, mapWrites)
  }

  val format: Format[Team] = {
    implicit val mf: Format[Map[RepoType, List[String]]] = mapFormat
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "createdDate"   ).formatNullable[Instant]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).formatNullable[Map[RepoType, List[String]]]
    ~ (__ \ "ownedRepos"    ).format[Seq[String]]
    )(Team.apply, unlift(Team.unapply))
  }
}
