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

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}
import play.api.mvc.QueryStringBindable

sealed trait Tag {def asString: String}

object Tag {
  case object AdminFrontend    extends Tag { def asString = "admin"              }
  case object Api              extends Tag { def asString = "api"                }
  case object BuiltOffPlatform extends Tag { def asString = "built-off-platform" }
  case object Maven            extends Tag { def asString = "maven"              }
  case object Stub             extends Tag { def asString = "stub"               }

  val values =
    Set(AdminFrontend, Api, BuiltOffPlatform, Maven, Stub)

  def parse(s: String): Either[String, Tag] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid tag - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format: Format[Tag] = new Format[Tag] {
    override def reads(json: JsValue): JsResult[Tag] =
      json.validate[String].flatMap(s => parse(s).fold(msg => JsError(msg), t => JsSuccess(t)))

    override def writes(o: Tag): JsValue = JsString(o.asString)
  }

  import cats.implicits._
  implicit val queryStringBindable: QueryStringBindable[List[Tag]] = new QueryStringBindable[List[Tag]] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, List[Tag]]] =
      params.get(key).map {
        case Nil  => Left("missing tag value")
        case tags => tags.toList.traverse(Tag.parse)
      }

    override def unbind(key: String, value: List[Tag]): String =
      value.map(t => s"$key=${t.asString}").mkString("&")
  }
}
