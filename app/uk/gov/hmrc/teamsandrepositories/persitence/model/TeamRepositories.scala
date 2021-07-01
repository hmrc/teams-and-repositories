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

package uk.gov.hmrc.teamsandrepositories.persitence.model

import java.time.{LocalDateTime, ZoneOffset}
import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.RepoType
import uk.gov.hmrc.teamsandrepositories._
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.controller.model.{Repository, RepositoryDetails, Team}

case class TeamRepositories(
  teamName    : String,
  repositories: List[GitRepository],
  updateDate  : Long
) {
  def toTeam(repositoriesToIgnore: List[String], excludeRepos: Boolean) = {
    val teamActivityDates =
      GitRepository.getTeamActivityDatesOfNonSharedRepos(repositories, repositoriesToIgnore)

    val repos =
      if (excludeRepos)
        None
      else
        Some(
          RepoType.values.map(repoType => repoType -> Seq.empty).toMap ++
            repositories
              .groupBy(_.repoType)
              .mapValues(_.map(_.name).distinct.sortBy(_.toUpperCase))
        )

    val ownedRepos = repositories.collect {
      case gitRepository if gitRepository.owningTeams.contains(teamName) => gitRepository.name
    }

    Team(
      name                     = teamName,
      firstActiveDate          = teamActivityDates.firstActiveDate,
      lastActiveDate           = teamActivityDates.lastActiveDate,
      firstServiceCreationDate = teamActivityDates.firstServiceCreationDate,
      repos                    = repos,
      ownedRepos               = ownedRepos
    )
  }
}

object TeamRepositories {
  case class DigitalServiceRepository(
    name         : String,
    createdAt    : Long,
    lastUpdatedAt: Long,
    repoType     : RepoType,
    teamNames    : Seq[String],
    archived     : Boolean
  )

  object DigitalServiceRepository {
    implicit val format: Format[DigitalServiceRepository] = {
      implicit val rtf = RepoType.format
      Json.format[DigitalServiceRepository]
    }
  }

  case class DigitalService(
    name         : String,
    lastUpdatedAt: Long,
    repositories : Seq[DigitalServiceRepository]
  )

  object DigitalService {
    implicit val format: Format[DigitalService] =
      Json.format[DigitalService]
  }

  def findDigitalServiceDetails(
    allTeamsAndRepos: Seq[TeamRepositories],
    digitalServiceName: String): Option[DigitalService] = {

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
      .map(Repository.create)
      .sortBy(_.name.toUpperCase) match {
      case Nil => None
      case repos =>
        Some(
          DigitalService(
            storedDigitalServiceName,
            repos.map(_.lastUpdatedAt).max,
            repos.map(
              repo =>
                DigitalServiceRepository(
                  repo.name,
                  repo.createdAt,
                  repo.lastUpdatedAt,
                  repo.repoType,
                  repoNameToTeamNamesLookup.getOrElse(repo.name, Seq(TEAM_UNKNOWN)),
                  repo.archived
                ))
          )
        )

    }
  }

  val TEAM_UNKNOWN = "TEAM_UNKNOWN"

  // TODO get rid of this, we shouldn't write datetime as a long...
  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats: OFormat[TeamRepositories] =
    Json.format[TeamRepositories]

  case class RepositoryToTeam(
    repositoryName: String,
    teamName      : String
  )

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] =
    teamRepos
      .flatMap(_.repositories)
      .groupBy(_.name)
      .map { case (_ , v) => v.maxBy(_.lastActiveDate) }
      .map(Repository.create)
      .toSeq
      .sortBy(_.name.toUpperCase)

  def findRepositoryDetails(
    teamRepos: Seq[TeamRepositories],
    repoName: String,
    ciUrlTemplates: UrlTemplates
  ): Option[RepositoryDetails] = {

    val teamsOwningRepo = teamRepos.filter {
      case TeamRepositories(_, repos, _) =>
        repos.exists(_.name.equalsIgnoreCase(repoName))
    }

    val maybeRepo: Option[GitRepository] = teamsOwningRepo.headOption.flatMap {
      case TeamRepositories(_, repos, _) =>
        repos.find(_.name.equalsIgnoreCase(repoName))
    }

    maybeRepo.map { repo =>
      RepositoryDetails.create(
        repo         = repo,
        teamNames    = teamsOwningRepo.filterNot(_.teamName == TEAM_UNKNOWN).map(_.teamName),
        urlTemplates = ciUrlTemplates
      )
    }
  }

  def getRepositoryDetailsList(
    teamRepos: Seq[TeamRepositories],
    repoType: RepoType,
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
          case TeamRepositories(teamName, repos, _) if repos.exists(_.name.equalsIgnoreCase(repo.name)) => teamName
        }

        RepositoryDetails.create(
          repo         = repo,
          teamNames    = teamNames.filterNot(_ == TEAM_UNKNOWN),
          urlTemplates = ciUrlTemplates
        )
      }
      .sortBy(_.name.toUpperCase)
  }

  def getRepositoryToTeamNameList(teamRepos: Seq[TeamRepositories]): Map[String, Seq[String]] = {
    val mappings = for {
      tr <- teamRepos
      r  <- tr.repositories
    } yield RepositoryToTeam(r.name, tr.teamName)

    mappings
      .groupBy(_.repositoryName)
      .map { m =>
        m._1 -> m._2.map(_.teamName).distinct
      }
  }
}
