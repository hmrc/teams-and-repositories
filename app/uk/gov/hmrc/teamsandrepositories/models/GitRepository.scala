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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.teamsandrepositories.connectors.BranchProtection

import java.time.Instant

case class GitRepository(
  name                : String,
  description         : String,
  url                 : String,
  createdDate         : Instant,
  lastActiveDate      : Instant,
  isPrivate           : Boolean                  = false,
  repoType            : RepoType                 = RepoType.Other,
  serviceType         : Option[ServiceType]      = None,
  digitalServiceName  : Option[String]           = None,
  owningTeams         : Seq[String]              = Nil,
  language            : Option[String],
  isArchived          : Boolean,
  defaultBranch       : String,
  branchProtection    : Option[BranchProtection] = None,
  isDeprecated        : Boolean                  = false,
  teams               : List[String]             = Nil,
  prototypeUrl        : Option[String]           = None
)

object GitRepository {

  val apiFormat: OFormat[GitRepository] = {
    implicit val rtf = RepoType.format
    implicit val stf = ServiceType.stFormat
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[Instant]
    ~ (__ \ "lastActiveDate"    ).format[Instant]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "serviceType"       ).formatNullable[ServiceType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "isArchived"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"     ).format[String]
    ~ (__ \ "branchProtection"  ).formatNullable(BranchProtection.format)
    ~ (__ \ "isDeprecated"      ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"         ).formatWithDefault[List[String]](Nil)
    ~ (__ \ "prototypeUrl"      ).formatNullable[String]
    )(apply, unlift(unapply))
  }

  val mongoFormat: OFormat[GitRepository] = {
    implicit val ldtf = MongoJavatimeFormats.instantFormat
    implicit val rtf = RepoType.format
    implicit val stf = ServiceType.stFormat
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[Instant]
    ~ (__ \ "lastActiveDate"    ).format[Instant]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "serviceType"       ).formatNullable[ServiceType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "isArchived"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"     ).formatWithDefault[String]("master")
    ~ (__ \ "branchProtection"  ).formatNullable(BranchProtection.format)
    ~ (__ \ "isDeprecated"      ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"         ).formatWithDefault[List[String]](Nil)
    ~ (__ \ "prototypeUrl"      ).formatNullable[String]
    )(apply, unlift(unapply))
  }
}
