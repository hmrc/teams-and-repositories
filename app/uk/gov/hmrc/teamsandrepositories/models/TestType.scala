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

import play.api.libs.json.*
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.teamsandrepositories.util.{FromString, Parser}
import uk.gov.hmrc.teamsandrepositories.util.FromStringEnum.*

enum TestType(val asString: String) extends FromString derives QueryStringBindable, Reads, Writes:
  case Acceptance  extends TestType("Acceptance")
  case Performance extends TestType("Performance")

object TestType:
  given Parser[TestType] = Parser.parser(TestType.values)

  val format: Format[TestType] = Format(derived$Reads, derived$Writes)
