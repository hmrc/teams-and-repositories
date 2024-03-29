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

sealed trait ServiceType {def asString: String}

object ServiceType {
  case object Frontend extends ServiceType { def asString = "frontend" }
  case object Backend  extends ServiceType { def asString = "backend"  }

  val values =
    Set(Frontend, Backend)

  def parse(s: String): Either[String, ServiceType] =
    values
      .find(_.asString.equalsIgnoreCase(s))
      .toRight(s"Invalid serviceType - should be one of: ${values.map(_.asString).mkString(", ")}")

  val format = new Format[ServiceType] {
    override def reads(json: JsValue): JsResult[ServiceType] =
      json match {
        case JsString(s) => parse(s).fold(msg => JsError(msg), x => JsSuccess(x))
        case _           => JsError("String value expected")
      }

    override def writes(o: ServiceType): JsValue = JsString(o.asString)
  }

  implicit val queryStringBindable: QueryStringBindable[ServiceType] = new QueryStringBindable[ServiceType] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ServiceType]] =
      params.get(key).map {
        case Nil         => Left("missing serviceType value")
        case head :: Nil => ServiceType.parse(head)
        case _           => Left("too many serviceType values")
      }

    override def unbind(key: String, value: ServiceType): String =
      s"$key=${value.asString}"
  }
}
