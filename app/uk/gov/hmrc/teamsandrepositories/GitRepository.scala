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
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType

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

    val reads: Reads[GitRepository] =
      ( (__ \ "name"              ).read[String]
      ~ (__ \ "description"       ).read[String]
      ~ (__ \ "url"               ).read[String]
      ~ (__ \ "createdDate"       ).read[Long]
      ~ (__ \ "lastActiveDate"    ).read[Long]
      ~ (__ \ "isPrivate"         ).readNullable[Boolean].map(_.getOrElse(false))
      ~ (__ \ "repoType"          ).read[RepoType]
      ~ (__ \ "digitalServiceName").readNullable[String]
      ~ (__ \ "owningTeams"       ).readNullable[Seq[String]].map(_.getOrElse(Nil))
      ~ (__ \ "language"          ).readNullable[String]
      ~ (__ \ "archived"          ).readNullable[Boolean].map(_.getOrElse(false))
      )(apply _)

    val writes = Json.writes[GitRepository]

    OFormat(reads, writes)
  }

  case class TeamActivityDates(
    firstActiveDate: Option[Long]          = None,
    lastActiveDate: Option[Long]           = None,
    firstServiceCreationDate: Option[Long] = None)

  def getTeamActivityDatesOfNonSharedRepos(
    repos: Seq[GitRepository],
    repositoriesToIgnore: List[String]): TeamActivityDates = {

    val nonIgnoredRepos = repos.filterNot(r => repositoriesToIgnore.contains(r.name))

    if (nonIgnoredRepos.nonEmpty) {
      val firstServiceCreationDate =
        if (nonIgnoredRepos.exists(_.repoType == RepoType.Service))
          Some(getCreatedAtDate(nonIgnoredRepos.filter(_.repoType == RepoType.Service)))
        else
          None

      TeamActivityDates(
        Some(getCreatedAtDate(nonIgnoredRepos)),
        Some(getLastActiveDate(nonIgnoredRepos)),
        firstServiceCreationDate)
    } else {
      TeamActivityDates()
    }
  }

  def primaryRepoType(repositories: Seq[GitRepository]): RepoType =
    if (repositories.exists(_.repoType == RepoType.Prototype)) RepoType.Prototype
    else if (repositories.exists(_.repoType == RepoType.Service)) RepoType.Service
    else if (repositories.exists(_.repoType == RepoType.Library)) RepoType.Library
    else RepoType.Other

  def getCreatedAtDate(repos: Seq[GitRepository]): Long =
    repos.minBy(_.createdDate).createdDate

  def getLastActiveDate(repos: Seq[GitRepository]): Long =
    repos.maxBy(_.lastActiveDate).lastActiveDate

  def extractRepositoryGroupForType(
    repoType: RepoType.RepoType,
    repositories: Seq[GitRepository]): List[GitRepository] =
    repositories
      .groupBy(_.name)
      .filter {
        case (_, repos) if repoType == RepoType.Service =>
          repos.exists(x => x.repoType == RepoType.Service)
        case (_, repos) if repoType == RepoType.Library =>
          !repos.exists(x => x.repoType == RepoType.Service) && repos.exists(x => x.repoType == RepoType.Library)
        case (_, repos) =>
          !repos.exists(x => x.repoType == RepoType.Service) && !repos.exists(x => x.repoType == RepoType.Library) && repos
            .exists(x => x.repoType == repoType)
      }
      .flatMap(_._2)
      .toList

}
