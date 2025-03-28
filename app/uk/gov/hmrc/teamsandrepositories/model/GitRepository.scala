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

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.teamsandrepositories.connector.BranchProtection

import java.time.Instant

case class GitRepository(
  name                : String,
  organisation        : Option[Organisation],
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
  teamNames           : Seq[String]              = Nil,
  prototypeName       : Option[String]           = None,
  prototypeAutoPublish: Option[Boolean]          = None,
  repositoryYamlText  : Option[String]           = None
)

object GitRepository:
  private given Format[Organisation]     = Organisation.format
  private given Format[RepoType]         = RepoType.format
  private given Format[ServiceType]      = ServiceType.format
  private given Format[TestType]         = TestType.format
  private given Format[Tag]              = Tag.format
  private given Format[BranchProtection] = BranchProtection.format

  val apiFormat: Format[GitRepository] =
    Json.format[GitRepository] // over 22 fields

  val mongoFormat: Format[GitRepository] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    Json.format[GitRepository] // over 22 fields
