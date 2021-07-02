/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.teamsandrepositories.util.DateTimeUtils

case class GitRepository(
  name              : String,
  description       : String,
  url               : String,
  createdDate       : LocalDateTime,
  lastActiveDate    : LocalDateTime,
  isPrivate         : Boolean        = false,
  repoType          : RepoType       = RepoType.Other,
  digitalServiceName: Option[String] = None,
  owningTeams       : Seq[String]    = Nil,
  language          : Option[String] = None,
  archived          : Boolean
)

object GitRepository {

  val apiFormat: OFormat[GitRepository] = {
    implicit val rtf = RepoType.format
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[LocalDateTime]
    ~ (__ \ "lastActiveDate"    ).format[LocalDateTime]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "archived"          ).formatWithDefault[Boolean](false)
    )(apply _, unlift(unapply))
  }

  val mongoFormat: OFormat[GitRepository] = {
    implicit val ldtf = MongoJavatimeFormats.localDateTimeFormat
    implicit val rtf = RepoType.format
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[LocalDateTime]
    ~ (__ \ "lastActiveDate"    ).format[LocalDateTime]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "archived"          ).formatWithDefault[Boolean](false)
    )(apply _, unlift(unapply))
  }

  case class TeamActivityDates(
    firstActiveDate         : Option[LocalDateTime] = None,
    lastActiveDate          : Option[LocalDateTime] = None,
    firstServiceCreationDate: Option[LocalDateTime] = None
  )

  def getTeamActivityDatesOfNonSharedRepos(
    repos               : Seq[GitRepository],
    repositoriesToIgnore: List[String]
  ): TeamActivityDates = {

    val nonIgnoredRepos = repos.filterNot(r => repositoriesToIgnore.contains(r.name))

    if (nonIgnoredRepos.nonEmpty) {
      val firstServiceCreationDate =
        if (nonIgnoredRepos.exists(_.repoType == RepoType.Service))
          Some(getCreatedAtDate(nonIgnoredRepos.filter(_.repoType == RepoType.Service)))
        else
          None

      TeamActivityDates(
        firstActiveDate          = Some(getCreatedAtDate(nonIgnoredRepos)),
        lastActiveDate           = Some(getLastActiveDate(nonIgnoredRepos)),
        firstServiceCreationDate = firstServiceCreationDate
      )
    } else
      TeamActivityDates()
  }

  def primaryRepoType(repositories: Seq[GitRepository]): RepoType =
    if      (repositories.exists(_.repoType == RepoType.Prototype)) RepoType.Prototype
    else if (repositories.exists(_.repoType == RepoType.Service  )) RepoType.Service
    else if (repositories.exists(_.repoType == RepoType.Library  )) RepoType.Library
    else RepoType.Other

  private implicit val ldto: Ordering[LocalDateTime] = DateTimeUtils.localDateTimeOrdering

  def getCreatedAtDate(repos: Seq[GitRepository]): LocalDateTime =
    repos.map(_.createdDate).min

  def getLastActiveDate(repos: Seq[GitRepository]): LocalDateTime =
    repos.map(_.lastActiveDate).max

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
