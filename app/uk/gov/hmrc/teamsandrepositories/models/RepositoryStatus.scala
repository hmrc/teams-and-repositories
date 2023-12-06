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

import play.api.libs.json._

trait RepositoryStatus { val asString: String }

object RepositoryStatus {
  object Archived extends RepositoryStatus { val asString: String = "Archived" }
  object BeingDecommissioned extends RepositoryStatus { val asString: String = "Being Decommissioned" }
  object Deprecated extends RepositoryStatus { val asString: String = "Deprecated" }

  val values: List[RepositoryStatus] = List(Archived, BeingDecommissioned, Deprecated)

  def parse(s: String): Either[String, RepositoryStatus] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid repository status - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[RepositoryStatus] =
    new Format[RepositoryStatus] {
      override def reads(json: JsValue): JsResult[RepositoryStatus] =
        json match {
          case JsString(s) => parse(s).fold(msg => JsError(msg), rt => JsSuccess(rt))
          case _           => JsError("String value expected")
        }

      override def writes(rt: RepositoryStatus): JsValue =
        JsString(rt.asString)
    }
}
