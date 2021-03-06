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

package uk.gov.hmrc.teamsandrepositories.controller.model

import java.time.Instant
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

case class Repository(
  name         : String,
  createdAt    : Instant,
  lastUpdatedAt: Instant,
  repoType     : RepoType,
  language     : Option[String],
  archived     : Boolean
)

object Repository {

  def create(gr: GitRepository): Repository =
    Repository(
      name          = gr.name,
      createdAt     = gr.createdDate,
      lastUpdatedAt = gr.lastActiveDate,
      repoType      = gr.repoType,
      language      = gr.language,
      archived      = gr.isArchived
    )

  implicit val format: OFormat[Repository] = {
    implicit val rtf = RepoType.format
    ( (__ \ "name"         ).format[String]
    ~ (__ \ "createdAt"    ).format[Instant]
    ~ (__ \ "lastUpdatedAt").format[Instant]
    ~ (__ \ "repoType"     ).format[RepoType]
    ~ (__ \ "language"     ).formatNullable[String]
    ~ (__ \ "archived"     ).format[Boolean]
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
    val mapReads: Reads[Map[RepoType, List[String]]] = new Reads[Map[RepoType, List[String]]] {
      def reads(jv: JsValue): JsResult[Map[RepoType, List[String]]] =
        JsSuccess(
          jv.as[Map[String, List[String]]].map {
            case (k, v) =>
              RepoType.parse(k).getOrElse(throw new NoSuchElementException()) -> v.asInstanceOf[List[String]]
          }
        )
    }

    val mapWrites: Writes[Map[RepoType, List[String]]] =
      new Writes[Map[RepoType, List[String]]] {
        def writes(map: Map[RepoType, List[String]]): JsValue =
          Json.obj(map.map {
            case (s, o) =>
              val ret: (String, JsValueWrapper) = s.toString -> JsArray(o.map(JsString.apply))
              ret
          }.toSeq: _*)
      }
    Format(mapReads, mapWrites)
  }

  val format: Format[Team] = {
    implicit val mf = mapFormat
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "createdDate"   ).formatNullable[Instant]
    ~ (__ \ "lastActiveDate").formatNullable[Instant]
    ~ (__ \ "repos"         ).formatNullable[Map[RepoType, List[String]]]
    ~ (__ \ "ownedRepos"    ).format[Seq[String]]
    )(Team.apply, unlift(Team.unapply))
  }
}
