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

import java.time.Instant

case class DeletedGitRepository(
  name                : String,
  deletedDate         : Instant,
  isPrivate           : Option[Boolean]       = None,
  repoType            : Option[RepoType]      = None,
  serviceType         : Option[ServiceType]   = None,
  digitalServiceName  : Option[String]        = None,
  owningTeams         : Option[Seq[String]]   = None,
  teams               : Option[List[String]]  = None,
  prototypeName       : Option[String]        = None,
)

object DeletedGitRepository {

  def fromGitRepository(gitRepository: GitRepository, deletedDate: Instant): DeletedGitRepository = {
    DeletedGitRepository(
      name               = gitRepository.name,
      deletedDate        = deletedDate,
      isPrivate          = Some(gitRepository.isPrivate),
      repoType           = Some(gitRepository.repoType),
      serviceType        = gitRepository.serviceType,
      digitalServiceName = gitRepository.digitalServiceName,
      owningTeams        = Some(gitRepository.owningTeams),
      teams              = Some(gitRepository.teams),
      prototypeName      = gitRepository.prototypeName
    )
  }

  val apiFormat: OFormat[DeletedGitRepository] = {
    implicit val rtf: Format[RepoType]    = RepoType.format
    implicit val stf: Format[ServiceType] = ServiceType.format
    ( (__ \ "name"                ).format[String]
    ~ (__ \ "deletedDate"         ).format[Instant]
    ~ (__ \ "isPrivate"           ).formatNullable[Boolean]
    ~ (__ \ "repoType"            ).formatNullable[RepoType]
    ~ (__ \ "serviceType"         ).formatNullable[ServiceType]
    ~ (__ \ "digitalServiceName"  ).formatNullable[String]
    ~ (__ \ "owningTeams"         ).formatNullable[Seq[String]]
    ~ (__ \ "teamNames"           ).formatNullable[List[String]]
    ~ (__ \ "prototypeName"       ).formatNullable[String]
    )(apply, d => Tuple.fromProductTyped(d))
  }

  val mongoFormat: OFormat[DeletedGitRepository] = {
    implicit val ldtf: Format[Instant]    = MongoJavatimeFormats.instantFormat
    implicit val rtf: Format[RepoType]    = RepoType.format
    implicit val stf: Format[ServiceType] = ServiceType.format
    ( (__ \ "name"                ).format[String]
    ~ (__ \ "deletedDate"         ).format[Instant]
    ~ (__ \ "isPrivate"           ).formatNullable[Boolean]
    ~ (__ \ "repoType"            ).formatNullable[RepoType]
    ~ (__ \ "serviceType"         ).formatNullable[ServiceType]
    ~ (__ \ "digitalServiceName"  ).formatNullable[String]
    ~ (__ \ "owningTeams"         ).formatNullable[Seq[String]]
    ~ (__ \ "teamNames"           ).formatNullable[List[String]]
    ~ (__ \ "prototypeName"       ).formatNullable[String]
    )(apply, d => Tuple.fromProductTyped(d))
  }
}
