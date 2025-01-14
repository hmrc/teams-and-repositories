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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.teamsandrepositories.connector.BranchProtection

import java.time.Instant

case class GitRepository(
  name                : String,
  description         : String,
  url                 : String,
  createdDate         : Instant,
  lastActiveDate      : Instant,
  endOfLifeDate       : Option[Instant]          = None,
  isPrivate           : Boolean                  = false,
  repoType            : RepoType                 = RepoType.Other,
  serviceType         : Option[ServiceType]      = None,
  testType            : Option[TestType]         = None,
  tags                : Option[Set[Tag]]         = None,
  digitalServiceName  : Option[String]           = None,
  owningTeams         : Seq[String]              = Nil,
  language            : Option[String]           = None,
  isArchived          : Boolean,
  defaultBranch       : String,
  branchProtection    : Option[BranchProtection] = None,
  isDeprecated        : Boolean                  = false,
  teams               : Seq[String]              = Nil,
  prototypeName       : Option[String]           = None,
  prototypeAutoPublish: Option[Boolean]          = None,
  repositoryYamlText  : Option[String]           = None
)

object GitRepository:

  val apiFormat: Format[GitRepository] =
    given Format[RepoType]    = RepoType.format
    given Format[ServiceType] = ServiceType.format
    given Format[TestType]    = TestType.format
    given Format[Tag]         = Tag.format
    ( (__ \ "name"                ).format[String]
    ~ (__ \ "description"         ).format[String]
    ~ (__ \ "url"                 ).format[String]
    ~ (__ \ "createdDate"         ).format[Instant]
    ~ (__ \ "lastActiveDate"      ).format[Instant]
    ~ (__ \ "endOfLifeDate"       ).formatNullable[Instant]
    ~ (__ \ "isPrivate"           ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"            ).format[RepoType]
    ~ (__ \ "serviceType"         ).formatNullable[ServiceType]
    ~ (__ \ "testType"            ).formatNullable[TestType]
    ~ (__ \ "tags"                ).format[Set[Tag]].inmap[Option[Set[Tag]]](Option.apply, _.getOrElse(Set.empty))
    ~ (__ \ "digitalServiceName"  ).formatNullable[String]
    ~ (__ \ "owningTeams"         ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"            ).formatNullable[String]
    ~ (__ \ "isArchived"          ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"       ).format[String]
    ~ (__ \ "branchProtection"    ).formatNullable(BranchProtection.format)
    ~ (__ \ "isDeprecated"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"           ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "prototypeName"       ).formatNullable[String]
    ~ (__ \ "prototypeAutoPublish").formatNullable[Boolean]
    ~ (__ \ "repositoryYamlText"  ).formatNullable[String]
    )(apply, g => Tuple.fromProductTyped(g))

  val mongoFormat: Format[GitRepository] =
    given Format[Instant]     = MongoJavatimeFormats.instantFormat
    given Format[RepoType]    = RepoType.format
    given Format[ServiceType] = ServiceType.format
    given Format[TestType]    = TestType.format
    given Format[Tag]         = Tag.format
    ( (__ \ "name"                ).format[String]
    ~ (__ \ "description"         ).format[String]
    ~ (__ \ "url"                 ).format[String]
    ~ (__ \ "createdDate"         ).format[Instant]
    ~ (__ \ "lastActiveDate"      ).format[Instant]
    ~ (__ \ "endOfLifeDate"       ).formatNullable[Instant]
    ~ (__ \ "isPrivate"           ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"            ).format[RepoType]
    ~ (__ \ "serviceType"         ).formatNullable[ServiceType]
    ~ (__ \ "testType"            ).formatNullable[TestType]
    ~ (__ \ "tags"                ).formatNullable[Set[Tag]]
    ~ (__ \ "digitalServiceName"  ).formatNullable[String]
    ~ (__ \ "owningTeams"         ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"            ).formatNullable[String]
    ~ (__ \ "isArchived"          ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"       ).formatWithDefault[String]("master")
    ~ (__ \ "branchProtection"    ).formatNullable(BranchProtection.format)
    ~ (__ \ "isDeprecated"        ).formatWithDefault[Boolean](false)
    ~ (__ \ "teamNames"           ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "prototypeName"       ).formatNullable[String]
    ~ (__ \ "prototypeAutoPublish").formatNullable[Boolean]
    ~ (__ \ "repositoryYamlText"  ).formatNullable[String]
    )(apply, g => Tuple.fromProductTyped(g))
