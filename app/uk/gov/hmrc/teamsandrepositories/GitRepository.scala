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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GitRepository(
  name              : String,
  description       : String,
  url               : String,
  createdDate       : Long,
  lastActiveDate    : Long,
  isPrivate         : Boolean        = false,
  repoType          : RepoType       = RepoType.Other,
  digitalServiceName: Option[String] = None,
  owningTeams       : Seq[String]    = Nil,
  language          : Option[String] = None,
  archived          : Boolean
)

object GitRepository {

  implicit val gitRepositoryFormats: OFormat[GitRepository] = {
    implicit val rtf = RepoType.format
    ( (__ \ "name"              ).format[String]
    ~ (__ \ "description"       ).format[String]
    ~ (__ \ "url"               ).format[String]
    ~ (__ \ "createdDate"       ).format[Long]
    ~ (__ \ "lastActiveDate"    ).format[Long]
    ~ (__ \ "isPrivate"         ).formatWithDefault[Boolean](false)
    ~ (__ \ "repoType"          ).format[RepoType]
    ~ (__ \ "digitalServiceName").formatNullable[String]
    ~ (__ \ "owningTeams"       ).formatWithDefault[Seq[String]](Nil)
    ~ (__ \ "language"          ).formatNullable[String]
    ~ (__ \ "archived"          ).formatWithDefault[Boolean](false)
    )(apply _, unlift(unapply))
  }

  case class TeamActivityDates(
    firstActiveDate         : Option[Long] = None,
    lastActiveDate          : Option[Long] = None,
    firstServiceCreationDate: Option[Long] = None
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

  def getCreatedAtDate(repos: Seq[GitRepository]): Long =
    repos.minBy(_.createdDate).createdDate

  def getLastActiveDate(repos: Seq[GitRepository]): Long =
    repos.maxBy(_.lastActiveDate).lastActiveDate

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
