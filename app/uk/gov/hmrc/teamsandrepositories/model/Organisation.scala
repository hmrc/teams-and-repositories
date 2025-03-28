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

enum Organisation(val asString: String) extends FromString derives QueryStringBindable, Reads, Writes:
  case Mdtp                   extends Organisation("mdtp")
  case External(name: String) extends Organisation(name)

object Organisation:
  given Parser[Organisation] =
    (s: String) =>
      s.toLowerCase match
        case Organisation.Mdtp.asString => Right(Organisation.Mdtp)
        case o                          => Right(Organisation.External(o))

  val format: Format[Organisation] =
    Format(derived$Reads, derived$Writes)
