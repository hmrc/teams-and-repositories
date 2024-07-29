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
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.controller.model.{Repository, RepositoryDetails, Team}

import java.time.Instant

case class TeamRepositories(
  teamName    : String,
  repositories: List[GitRepository],
  createdDate : Option[Instant],
  updateDate  : Instant
):
  def toTeam(sharedRepos: List[String], includeRepos: Boolean) =

    val lastActiveDate =
      val exclusiveRepos = repositories.filterNot(r => sharedRepos.contains(r.name))
      if exclusiveRepos.isEmpty then None else Some(exclusiveRepos.map(_.lastActiveDate).max)

    val repos =
      if !includeRepos then
        None
      else
        Some(
          RepoType.values.map(repoType => repoType -> List.empty).toMap ++
            repositories
              .groupBy(_.repoType)
              .view
              .mapValues(_.map(_.name).distinct.sortBy(_.toUpperCase))
              .toMap
        )

    val ownedRepos = repositories.collect:
      case gitRepository if gitRepository.owningTeams.contains(teamName) => gitRepository.name

    Team(
      name           = teamName,
      createdDate    = createdDate,
      lastActiveDate = lastActiveDate,
      repos          = repos,
      ownedRepos     = ownedRepos.sortBy(_.toUpperCase)
    )

object TeamRepositories:
  private val TEAM_UNKNOWN = "TEAM_UNKNOWN"

  val apiFormat: OFormat[TeamRepositories] =
    given OFormat[GitRepository] = GitRepository.apiFormat
    ( (__ \ "teamName"    ).format[String]
    ~ (__ \ "repositories").format[List[GitRepository]]
    ~ (__ \ "createdDate" ).formatNullable[Instant]
    ~ (__ \ "updateDate"  ).format[Instant]
    )(apply, t => Tuple.fromProductTyped(t))

  val mongoFormat: OFormat[TeamRepositories] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    given OFormat[GitRepository] = GitRepository.mongoFormat
    ( (__ \ "teamName"    ).format[String]
    ~ (__ \ "repositories").format[List[GitRepository]]
    ~ (__ \ "createdDate" ).formatNullable[Instant]
    ~ (__ \ "updateDate"  ).format[Instant]
    )(TeamRepositories.apply, t => Tuple.fromProductTyped(t))

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] =
    val repoTeams =
      teamRepos
        .flatMap(teamRepo => teamRepo.repositories.map(_.name -> teamRepo.teamName))
        .groupBy(_._1).view.mapValues(_.map(a => a._2)).toMap

    teamRepos
      .flatMap(_.repositories)
      .groupBy(_.name)
      .map { case (_, v) => v.maxBy(_.lastActiveDate) }
      .map(gitRepository => Repository.create(gitRepository, repoTeams.getOrElse(gitRepository.name, Seq(TEAM_UNKNOWN))))
      .toSeq
      .sortBy(_.name.toUpperCase)

  def findRepositoryDetails(
    teamRepos     : Seq[TeamRepositories],
    repoName      : String,
    ciUrlTemplates: UrlTemplates
  ): Option[RepositoryDetails] =

    val teamsOwningRepo =
      teamRepos.filter(_.repositories.exists(_.name.equalsIgnoreCase(repoName)))

    val maybeRepo: Option[GitRepository] =
      teamsOwningRepo.headOption.flatMap(_.repositories.find(_.name.equalsIgnoreCase(repoName)))

    maybeRepo.map: repo =>
      RepositoryDetails.create(
        repo         = repo,
        teamNames    = teamsOwningRepo.filterNot(_.teamName == TEAM_UNKNOWN).map(_.teamName),
        urlTemplates = ciUrlTemplates
      )
