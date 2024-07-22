/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.util

import play.api.data.format.Formatter
import play.api.libs.json.{JsError, JsString, JsSuccess, Reads, Writes}
import play.api.mvc.{PathBindable, QueryStringBindable}

trait FromString { def asString: String }

trait Parser[T] { def parse(s: String): Either[String, T] }

object Parser:
  def parser[T <: FromString](values: Array[T]): Parser[T] =
    (s: String) =>
      values
        .find(_.asString.equalsIgnoreCase(s))
        .toRight(s"Invalid value: \"$s\" - should be one of: ${values.map(_.asString).mkString(", ")}")

  @inline def apply[T](using instance: Parser[T]): Parser[T] = instance


trait FormFormat[A] extends Formatter[A]

object FromStringEnum:
  extension (obj: Ordering.type)
    def derived[A <: scala.reflect.Enum]: Ordering[A] =
      Ordering.by(_.ordinal)

  extension (obj: Writes.type)
    def derived[A <: FromString]: Writes[A] =
      a => JsString(a.asString)

  extension (obj: Reads.type)
    def derived[A : Parser]: Reads[A] =
      _.validate[String]
        .flatMap(Parser[A].parse(_).fold(JsError(_), JsSuccess(_)))

  extension (obj: PathBindable.type)
    def derived[A <: FromString : Parser]: PathBindable[A] =
      Binders.pathBindableFromString(Parser[A].parse, _.asString)

  extension (obj: QueryStringBindable.type)
    def derived[A <: FromString : Parser]: QueryStringBindable[A] =
      Binders.queryStringBindableFromString(
        s => Some(Parser[A].parse(s)),
        _.asString
      )
