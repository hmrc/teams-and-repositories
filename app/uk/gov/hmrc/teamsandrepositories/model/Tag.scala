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

import play.api.libs.json.*
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.teamsandrepositories.util.{FromString, Parser}
import uk.gov.hmrc.teamsandrepositories.util.FromStringEnum.*

enum Tag(val asString: String) extends FromString derives Reads, Writes:
  case AdminFrontend    extends Tag("admin"             )
  case Api              extends Tag("api"               )
  case BuiltOffPlatform extends Tag("built-off-platform")
  case External         extends Tag("external"          )
  case Maven            extends Tag("maven"             )
  case Stub             extends Tag("stub"              )

object Tag:
  given Parser[Tag] = Parser.parser(Tag.values)

  val format: Format[Tag] = Format(derived$Reads, derived$Writes)

  import cats.implicits._
  given QueryStringBindable[List[Tag]] =
      new QueryStringBindable[List[Tag]] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, List[Tag]]] =
        params.get(key).map:
          case Nil  => Left("missing tag value")
          case tags => tags.toList.traverse(Parser[Tag].parse)

      override def unbind(key: String, value: List[Tag]): String =
        value.map(t => s"$key=${t.asString}").mkString("&")
    }
