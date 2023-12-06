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
import uk.gov.hmrc.teamsandrepositories.models.RepositoryStatus
import uk.gov.hmrc.teamsandrepositories.util.DateTimeUtils

import java.time.Instant

case class TeamRepositories(
  teamName    : String,
  repositories: List[GitRepository],
  createdDate : Option[Instant],
  updateDate  : Instant
) {
  def toTeam(sharedRepos: List[String], includeRepos: Boolean) = {

    val lastActiveDate = {
      val exclusiveRepos = repositories.filterNot(r => sharedRepos.contains(r.name))
      if (exclusiveRepos.isEmpty) None else Some(exclusiveRepos.map(_.lastActiveDate).max)
    }

    val repos =
      if (!includeRepos)
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

    val ownedRepos = repositories.collect {
      case gitRepository if gitRepository.owningTeams.contains(teamName) => gitRepository.name
    }

    Team(
      name           = teamName,
      createdDate    = createdDate,
      lastActiveDate = lastActiveDate,
      repos          = repos,
      ownedRepos     = ownedRepos
    )
  }
}

object TeamRepositories {
  private implicit val io: Ordering[Instant] = DateTimeUtils.instantOrdering

  def findDigitalServiceDetails(
    allTeamsAndRepos   : Seq[TeamRepositories],
    digitalServiceName: String
  ): Option[DigitalService] = {

    case class RepoAndTeam(repositoryName: String, teamName: String)

    val repoNameToTeamNamesLookup: Map[String, Seq[String]] =
      allTeamsAndRepos
        .flatMap(teamAndRepo => teamAndRepo.repositories.map(repo => RepoAndTeam(repo.name, teamAndRepo.teamName)))
        .groupBy(_.repositoryName)
        .map {
          case (repositoryName, repoAndTeams) => (repositoryName, repoAndTeams.map(_.teamName).distinct)
        }

    val gitReposForDigitalService =
      allTeamsAndRepos
        .flatMap(_.repositories)
        .filter(_.digitalServiceName.exists(_.equalsIgnoreCase(digitalServiceName)))

    val storedDigitalServiceName: String =
      gitReposForDigitalService.headOption.flatMap(_.digitalServiceName).getOrElse(digitalServiceName)

    gitReposForDigitalService.distinct
      .map(gitRepository => Repository.create(gitRepository,
        repoNameToTeamNamesLookup.getOrElse(gitRepository.name, Seq(TEAM_UNKNOWN))))
      .sortBy(_.name.toUpperCase) match {
        case Nil   => None
        case repos => Some(DigitalService(
                        name          = storedDigitalServiceName,
                        lastUpdatedAt = repos.map(_.lastUpdatedAt).max,
                        repositories  = repos.map(repo =>
                                          DigitalServiceRepository(
                                            repo.name,
                                            repo.createdAt,
                                            repo.lastUpdatedAt,
                                            repo.repoType,
                                            repoNameToTeamNamesLookup.getOrElse(repo.name, Seq(TEAM_UNKNOWN)),
                                            repo.status.map(_ == RepositoryStatus.Archived).getOrElse(false)
                                          )
                                        )
                     ))
      }
  }

  def unknown(repositories: List[GitRepository], updateDate: Instant): TeamRepositories =
    TeamRepositories(
      teamName     = TEAM_UNKNOWN,
      repositories = repositories,
      createdDate  = None,
      updateDate   = updateDate
    )

  val TEAM_UNKNOWN = "TEAM_UNKNOWN"

  val apiFormat: OFormat[TeamRepositories] = {
    implicit val grf = GitRepository.apiFormat
    ( (__ \ "teamName"    ).format[String]
    ~ (__ \ "repositories").format[List[GitRepository]]
    ~ (__ \ "createdDate" ).formatNullable[Instant]
    ~ (__ \ "updateDate"  ).format[Instant]
    )(apply, unlift(unapply))
  }

  val mongoFormat: OFormat[TeamRepositories] = {
    implicit val inf = MongoJavatimeFormats.instantFormat
    implicit val grf = GitRepository.mongoFormat
    ( (__ \ "teamName"    ).format[String]
    ~ (__ \ "repositories").format[List[GitRepository]]
    ~ (__ \ "createdDate" ).formatNullable[Instant]
    ~ (__ \ "updateDate"  ).format[Instant]
    )(TeamRepositories.apply, unlift(TeamRepositories.unapply))
  }

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] = {
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
  }

  def findRepositoryDetails(
    teamRepos     : Seq[TeamRepositories],
    repoName      : String,
    ciUrlTemplates: UrlTemplates
  ): Option[RepositoryDetails] = {

    val teamsOwningRepo =
      teamRepos.filter(_.repositories.exists(_.name.equalsIgnoreCase(repoName)))

    val maybeRepo: Option[GitRepository] =
      teamsOwningRepo.headOption.flatMap(_.repositories.find(_.name.equalsIgnoreCase(repoName)))

    maybeRepo.map { repo =>
      RepositoryDetails.create(
        repo         = repo,
        teamNames    = teamsOwningRepo.filterNot(_.teamName == TEAM_UNKNOWN).map(_.teamName),
        urlTemplates = ciUrlTemplates
      )
    }
  }

  def getRepositoryDetailsList(
    teamRepos     : Seq[TeamRepositories],
    repoType      : RepoType,
    ciUrlTemplates: UrlTemplates
  ): Seq[RepositoryDetails] = {

    val allReposForType =
      teamRepos
        .flatMap(_.repositories)
        .distinct
        .filter(_.repoType == repoType)

    allReposForType
      .map { repo =>
        val teamNames = teamRepos.collect {
          case teamRepository if teamRepository.repositories.exists(_.name.equalsIgnoreCase(repo.name)) => teamRepository.teamName
        }

        RepositoryDetails.create(
          repo         = repo,
          teamNames    = teamNames.filterNot(_ == TEAM_UNKNOWN),
          urlTemplates = ciUrlTemplates
        )
      }
      .sortBy(_.name.toUpperCase)
  }

  def getRepositoryToTeamNames(teamRepos: Seq[TeamRepositories]): Map[String, Seq[String]] = {
    val mappings = for {
      tr <- teamRepos
      r  <- tr.repositories
    } yield (r.name, tr.teamName)

    mappings
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).distinct)
      .toMap
  }
}

case class DigitalServiceRepository(
  name         : String,
  createdAt    : Instant,
  lastUpdatedAt: Instant,
  repoType     : RepoType,
  teamNames    : Seq[String],
  archived     : Boolean
)

object DigitalServiceRepository {
  val format: Format[DigitalServiceRepository] = {
    implicit val rtf = RepoType.format
    ( (__ \ "name"         ).format[String]
    ~ (__ \ "createdAt"    ).format[Instant]
    ~ (__ \ "lastUpdatedAt").format[Instant]
    ~ (__ \ "repoType"     ).format[RepoType]
    ~ (__ \ "teamNames"    ).format[Seq[String]]
    ~ (__ \ "archived"     ).format[Boolean]
    )(apply, unlift(unapply))
  }
}

case class DigitalService(
  name         : String,
  lastUpdatedAt: Instant,
  repositories : Seq[DigitalServiceRepository]
)

object DigitalService {
  val format: Format[DigitalService] = {
    implicit val dsrf = DigitalServiceRepository.format
    ( (__ \ "name"         ).format[String]
    ~ (__ \ "lastUpdatedAt").format[Instant]
    ~ (__ \ "repositories" ).format[Seq[DigitalServiceRepository]]
    )(apply, unlift(unapply))
  }
}
