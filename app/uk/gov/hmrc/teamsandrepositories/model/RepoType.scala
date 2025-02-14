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

enum RepoType(val asString: String) extends FromString derives QueryStringBindable, Reads, Writes:
  case Service   extends RepoType("Service"  )
  case Library   extends RepoType("Library"  )
  case Prototype extends RepoType("Prototype")
  case Test      extends RepoType("Test"     )
  case Other     extends RepoType("Other"    )

object RepoType:
  given Parser[RepoType] = Parser.parser(RepoType.values)

  val format: Format[RepoType] = Format(derived$Reads, derived$Writes)
