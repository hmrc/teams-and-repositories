/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories

import java.time.Instant
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.teamsandrepositories.connectors.{GhBranchProtection, GhRepository}

case class GitRepository(
  name              : String,
  description       : String,
  url               : String,
  createdDate       : Instant,
  lastActiveDate    : Instant,
  isPrivate         : Boolean                     = false,
  repoType          : RepoType                    = RepoType.Other,
  digitalServiceName: Option[String]              = None,
  owningTeams       : Seq[String]                 = Nil,
  language          : Option[String],
  isArchived        : Boolean,
  defaultBranch     : String,
  branchProtection  : Option[GhBranchProtection] = None
) {

  /**
   * Returns true if this `GitRepository`'s inputs (in terms of a `GhRepository`) are unchanged, false otherwise.
   *
   * This ensures that a cache entry for a given repository may be correctly invalidated for non-code data changes that
   * would not be picked up by inspecting a repository's last `pushedAt` timestamp.
   */
  def inputsAreUnchanged(ghRepository: GhRepository): Boolean = {
    val projection =
      GhRepository(
        name               = name,
        description        = Some(description),
        htmlUrl            = url,
        fork               = ghRepository.fork,
        createdDate        = createdDate,
        pushedAt           = lastActiveDate,
        isPrivate          = isPrivate,
        language           = language,
        isArchived         = isArchived,
        defaultBranch      = defaultBranch,
        branchProtection   = branchProtection,
        repoTypeHeuristics = ghRepository.repoTypeHeuristics
      )

    ghRepository == projection
  }
}

object GitRepository {

  val apiFormat: OFormat[GitRepository] = {
    implicit val rtf = RepoType.format
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[Instant]
    ~ (__ \ "lastActiveDate"    ).format[Instant]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "archived"          ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"     ).format[String]
    ~ (__ \ "branchProtection"  ).formatNullable(GhBranchProtection.format)
    )(apply _, unlift(unapply))
  }

  val mongoFormat: OFormat[GitRepository] = {
    implicit val ldtf = MongoJavatimeFormats.instantFormat
    implicit val rtf = RepoType.format
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[Instant]
    ~ (__ \ "lastActiveDate"    ).format[Instant]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "archived"          ).formatWithDefault[Boolean](false)
    ~ (__ \ "defaultBranch"     ).formatWithDefault[String]("master")
    ~ (__ \ "branchProtection"  ).formatNullable(GhBranchProtection.format)
    )(apply _, unlift(unapply))
  }

  def primaryRepoType(repositories: Seq[GitRepository]): RepoType =
    if      (repositories.exists(_.repoType == RepoType.Prototype)) RepoType.Prototype
    else if (repositories.exists(_.repoType == RepoType.Service  )) RepoType.Service
    else if (repositories.exists(_.repoType == RepoType.Library  )) RepoType.Library
    else RepoType.Other

  def extractRepositoryGroupForType(
    repoType    : RepoType,
    repositories: Seq[GitRepository]
  ): List[GitRepository] =
    repositories
      .groupBy(_.name)
      .filter {
        case (_, repos) if repoType == RepoType.Service =>  repos.exists(_.repoType == RepoType.Service)
        case (_, repos) if repoType == RepoType.Library => !repos.exists(_.repoType == RepoType.Service) &&
                                                            repos.exists(_.repoType == RepoType.Library)
        case (_, repos)                                 => !repos.exists(_.repoType == RepoType.Service) &&
                                                           !repos.exists(_.repoType == RepoType.Library) &&
                                                            repos.exists(_.repoType == repoType)
      }
      .flatMap(_._2)
      .toList
}
