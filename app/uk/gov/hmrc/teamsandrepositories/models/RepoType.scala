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

import play.api.libs.json._

sealed trait RepoType { def asString: String }

object RepoType {

  case object Service   extends RepoType { override val asString = "Service"   }
  case object Library   extends RepoType { override val asString = "Library"   }
  case object Prototype extends RepoType { override val asString = "Prototype" }
  case object Other     extends RepoType { override val asString = "Other"     }

  val values: List[RepoType] = List(Service, Library, Prototype, Other)

  def parse(s: String): Either[String, RepoType] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid repoType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[RepoType] = new Format[RepoType] {
    override def reads(json: JsValue): JsResult[RepoType] =
      json match {
        case JsString(s) =>
          parse(s).fold(msg => JsError(msg), rt => JsSuccess(rt))
        case _ => JsError("String value expected")
      }

    override def writes(o: RepoType): JsValue =
      JsString(o.asString)
  }
}
