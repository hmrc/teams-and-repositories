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

import play.api.libs.functional.syntax._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

case class Repository(
  name         : String,
  createdAt    : Long,
  lastUpdatedAt: Long,
  repoType     : RepoType.RepoType,
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
      archived      = gr.archived
    )

  implicit val format: OFormat[Repository] = Json.format[Repository]
}

case class Team(
  name                    : String,
  firstActiveDate         : Option[Long] = None,
  lastActiveDate          : Option[Long] = None,
  firstServiceCreationDate: Option[Long] = None,
  repos                   : Option[Map[RepoType.Value, Seq[String]]],
  ownedRepos              : Seq[String]  = Nil
)

object Team {

  val mapReads: Reads[Map[RepoType.RepoType, Seq[String]]] = new Reads[Map[RepoType.RepoType, Seq[String]]] {
    def reads(jv: JsValue): JsResult[Map[RepoType.RepoType, Seq[String]]] =
      JsSuccess(jv.as[Map[String, Seq[String]]].map {
        case (k, v) =>
          RepoType.withName(k) -> v.asInstanceOf[Seq[String]]
      })
  }

  val mapWrites: Writes[Map[RepoType.RepoType, Seq[String]]] =
    new Writes[Map[RepoType.RepoType, Seq[String]]] {
      def writes(map: Map[RepoType.RepoType, Seq[String]]): JsValue =
        Json.obj(map.map {
          case (s, o) =>
            val ret: (String, JsValueWrapper) = s.toString -> JsArray(o.map(JsString.apply))
            ret
        }.toSeq: _*)
    }

  val mapFormat: Format[Map[RepoType.RepoType, Seq[String]]] = Format(mapReads, mapWrites)

  val format: Format[Team] = {
    implicit val mf = mapFormat
    ( (__ \ "name"                    ).format[String]
    ~ (__ \ "firstActiveDate"         ).formatNullable[Long] // TODO should be Date...
    ~ (__ \ "lastActiveDate"          ).formatNullable[Long]  // TODO should be Date...
    ~ (__ \ "firstServiceCreationDate").formatNullable[Long] // TODO should be Date...
    ~ (__ \ "repos"                   ).formatNullable[Map[RepoType.Value, Seq[String]]] // TODO should this be nullable?
    ~ (__ \ "ownedRepos"              ).format[Seq[String]]
    )(Team.apply, unlift(Team.unapply))
  }
}
