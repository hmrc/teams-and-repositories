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
import play.api.mvc.QueryStringBindable

enum RepoType(val asString: String):
  case Service   extends RepoType("Service"  )
  case Library   extends RepoType("Library"  )
  case Prototype extends RepoType("Prototype")
  case Test      extends RepoType("Test"     )
  case Other     extends RepoType("Other"    )

object RepoType:
  def parse(s: String): Either[String, RepoType] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid repoType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[RepoType] =
    new Format[RepoType] {
      override def reads(json: JsValue): JsResult[RepoType] =
        json match
          case JsString(s) => parse(s).fold(msg => JsError(msg), x => JsSuccess(x))
          case _           => JsError("String value expected")

      override def writes(o: RepoType): JsValue =
        JsString(o.asString)
  }

  given QueryStringBindable[RepoType] =
    new QueryStringBindable[RepoType] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, RepoType]] =
        params.get(key).map:
          case Nil         => Left("missing repoType value")
          case head :: Nil => RepoType.parse(head)
          case _           => Left("too many repoType values")

      override def unbind(key: String, value: RepoType): String =
        s"$key=${value.asString}"
    }
