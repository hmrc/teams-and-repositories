/*
 * Copyright 2016 HM Revenue & Customs
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

import java.net.URLDecoder
import java.time.LocalDateTime

import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}


object TeamRepositoryWrapper {

  private case class TeamActivityDates(firstActiveDate: Option[Long] = None,
                                       lastActiveDate: Option[Long] = None,
                                       firstServiceCreationDate: Option[Long] = None)

  private case class RepositoriesToTeam(repositories: Seq[GitRepository], teamName: String)

  def getTeamList(teamRepos: Seq[TeamRepositories], repositoriesToIgnore: List[String]): Seq[Team] =
    teamRepos.map(_.teamName).map { tn =>
      val repos: Seq[GitRepository] = teamRepos.filter(_.teamName == tn).flatMap(_.repositories)
      val team = Team(name = tn, repos = None)
      if (repos.nonEmpty) {
        val teamActivityDates = getTeamActivityDatesOfNonSharedRepos(repos, repositoriesToIgnore)
        team.copy(firstActiveDate = teamActivityDates.firstActiveDate, lastActiveDate = teamActivityDates.lastActiveDate)
      } else team

    }

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] =
    teamRepos
      .flatMap(_.repositories)
      .groupBy(_.name)
      .map {
        case (repositoryName, repositories) =>
          Repository(
            repositoryName,
            repositories.minBy(_.createdDate).createdDate,
            repositories.maxBy(_.lastActiveDate).lastActiveDate,
            primaryRepoType(repositories))
      }
      .toList
      .sortBy(_.name.toUpperCase)

  def findRepositoryDetails(teamRepos: Seq[TeamRepositories], repoName: String, ciUrlTemplates: UrlTemplates): Option[RepositoryDetails] = {
    teamRepos.foldLeft((Set.empty[String], Set.empty[GitRepository])) { case ((ts, repos), tr) =>
      if (tr.repositories.exists(_.name == repoName))
        (ts + tr.teamName, repos ++ tr.repositories.filter(_.name == repoName))
      else (ts, repos)
    } match {
      case (teams, repos) if repos.nonEmpty =>
        repoGroupToRepositoryDetails(primaryRepoType(repos.toSeq), repos.toSeq, teams.toSeq.sorted, ciUrlTemplates)
      case _ => None
    }
  }

  def getRepositoryDetailsList(teamRepos: Seq[TeamRepositories], repoType: RepoType, ciUrlTemplates: UrlTemplates): Seq[RepositoryDetails] = {
    getRepositoryTeams(teamRepos)
      .groupBy(_.repositories)
      .flatMap { case (repositories, teamsAndRepos: Seq[RepositoriesToTeam]) => repoGroupToRepositoryDetails(repoType, repositories, teamsAndRepos.map(_.teamName), ciUrlTemplates) }
      .toSeq
      .sortBy(_.name.toUpperCase)
  }

  private def primaryRepoType(repositories: Seq[GitRepository]): RepoType = {
    if (repositories.exists(_.repoType == RepoType.Prototype)) RepoType.Prototype
    else if (repositories.exists(_.repoType == RepoType.Service)) RepoType.Service
    else if (repositories.exists(_.repoType == RepoType.Library)) RepoType.Library
    else RepoType.Other
  }


  def getTeamRepositoryNameList(teamRepos: Seq[TeamRepositories], teamName: String): Option[Map[RepoType.RepoType, List[String]]] = {
    val decodedTeamName = URLDecoder.decode(teamName, "UTF-8")
    teamRepos.find(_.teamName == decodedTeamName).map { t =>

      RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, rtype) =>
        m + (rtype -> extractRepositoryGroupForType(rtype, t.repositories).map(_.name).distinct.sortBy(_.toUpperCase))
      }

    }
  }

  def findTeam(teamRepos: Seq[TeamRepositories], teamName: String, repositoriesToIgnore: List[String]): Option[Team] = {

    teamRepos
      .find(_.teamName == URLDecoder.decode(teamName, "UTF-8"))
      .map { teamRepositories =>

        val teamActivityDates = getTeamActivityDatesOfNonSharedRepos(teamRepositories.repositories, repositoriesToIgnore)

        def getRepositoryDisplayDetails(repoType: RepoType.Value): List[String] = {
          teamRepositories.repositories
            .filter(_.repoType == repoType)
            .map(_.name)
            .distinct
            .sortBy(_.toUpperCase)
        }

        val repos = RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, repoType) =>
          m + (repoType -> getRepositoryDisplayDetails(repoType))
        }

        Team(teamName, teamActivityDates.firstActiveDate, teamActivityDates.lastActiveDate, teamActivityDates.firstServiceCreationDate, Some(repos))
      }

  }

  private case class RepositoryToTeam(repositoryName: String, teamName: String)

  def getRepositoryToTeamNameList(teamRepos: Seq[TeamRepositories]): Map[String, Seq[String]] = {
    val mappings = for {
      tr <- teamRepos
      r <- tr.repositories
    } yield RepositoryToTeam(r.name, tr.teamName)

    mappings.groupBy(_.repositoryName)
      .map { m => m._1 -> m._2.map(_.teamName).distinct }
  }


  private def getTeamActivityDatesOfNonSharedRepos(repos: Seq[GitRepository], repositoriesToIgnore: List[String]): TeamActivityDates = {

    val nonIgnoredRepos = repos.filterNot(r => repositoriesToIgnore.contains(r.name))

    if (nonIgnoredRepos.nonEmpty) {
      val firstServiceCreationDate =
        if (nonIgnoredRepos.exists(_.repoType == RepoType.Service))
          Some(getCreatedAtDate(nonIgnoredRepos.filter(_.repoType == RepoType.Service)))
        else
          None

      TeamActivityDates(Some(getCreatedAtDate(nonIgnoredRepos)), Some(getLastActiveDate(nonIgnoredRepos)), firstServiceCreationDate)
    }
    else {
      TeamActivityDates()
    }
  }

  private def getCreatedAtDate(repos: Seq[GitRepository]) =
    repos.minBy(_.createdDate).createdDate

  private def getLastActiveDate(repos: Seq[GitRepository]) =
    repos.maxBy(_.lastActiveDate).lastActiveDate

  private def getRepositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoriesToTeam] =
    for {
      teamAndRepositories <- data
      repositories <- teamAndRepositories.repositories.groupBy(_.name).values
    } yield RepositoriesToTeam(repositories, teamAndRepositories.teamName)

  def repoGroupToRepositoryDetails(repoType: RepoType,
                                   repositories: Seq[GitRepository],
                                   teamNames: Seq[String],
                                   urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    val primaryRepository = extractRepositoryGroupForType(repoType, repositories).find(_.repoType == repoType)

    buildRepositoryDetails(primaryRepository, repositories, teamNames, urlTemplates)

  }

  private def buildRepositoryDetails(primaryRepository: Option[GitRepository],
                                     allRepositories: Seq[GitRepository],
                                     teamNames: Seq[String],
                                     urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    primaryRepository.map { repo =>

      val sameNameRepos: Seq[GitRepository] = allRepositories.filter(r => r.name == repo.name)
      val createdDate = sameNameRepos.minBy(_.createdDate).createdDate
      val lastActiveDate = sameNameRepos.maxBy(_.lastActiveDate).lastActiveDate

      val repoDetails = RepositoryDetails(
        repo.name,
        repo.description,
        createdDate,
        lastActiveDate,
        repo.repoType,
        teamNames,
        allRepositories.map { repo =>
          Link(
            githubName(repo.isInternal),
            githubDisplayName(repo.isInternal),
            repo.url)
        })

      val repositoryForCiUrls: GitRepository = allRepositories.find(!_.isInternal).fold(repo)(identity)

      if (hasEnvironment(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates), environments = buildEnvironmentUrls(repo, urlTemplates))
      else if (hasBuild(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates))
      else repoDetails
    }
  }

  private def githubName(isInternal: Boolean) = if (isInternal) "github-enterprise" else "github-com"

  private def githubDisplayName(isInternal: Boolean) = if (isInternal) "Github Enterprise" else "GitHub.com"

  private def hasEnvironment(repo: GitRepository): Boolean = repo.repoType == RepoType.Service

  private def hasBuild(repo: GitRepository): Boolean = repo.repoType == RepoType.Library

  private def buildEnvironmentUrls(repository: GitRepository, urlTemplates: UrlTemplates): Seq[Environment] = {
    urlTemplates.environments.map { case (name, tps) =>
      val links = tps.map { tp => Link(tp.name, tp.displayName, tp.url(repository.name)) }
      Environment(name, links)
    }.toSeq
  }

  private def buildCiUrls(repository: GitRepository, urlTemplates: UrlTemplates): List[Link] =
    repository.isInternal match {
      case true => buildUrls(repository, urlTemplates.ciClosed)
      case false => buildUrls(repository, urlTemplates.ciOpen)
    }

  private def buildUrls(repo: GitRepository, templates: Seq[UrlTemplate]) =
    templates.map(t => Link(t.name, t.displayName, t.url(repo.name))).toList

  def extractRepositoryGroupForType(repoType: RepoType.RepoType, repositories: Seq[GitRepository]): List[GitRepository] = {
    repositories
      .groupBy(_.name)
      .filter {
        case (name, repos) if repoType == RepoType.Service =>
          repos.exists(x => x.repoType == RepoType.Service)
        case (name, repos) if repoType == RepoType.Library =>
          !repos.exists(x => x.repoType == RepoType.Service) && repos.exists(x => x.repoType == RepoType.Library)
        case (name, repos) =>
          !repos.exists(x => x.repoType == RepoType.Service) && !repos.exists(x => x.repoType == RepoType.Library) && repos.exists(x => x.repoType == repoType)
      }
      .flatMap(_._2).toList
  }

}
