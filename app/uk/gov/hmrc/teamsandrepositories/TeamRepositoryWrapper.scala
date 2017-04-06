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

import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}


object TeamRepositoryWrapper {

  case class TeamActivityDates(firstActiveDate: Option[Long] = None,
                                       lastActiveDate: Option[Long] = None,
                                       firstServiceCreationDate: Option[Long] = None)

  case class RepositoriesToTeam(repositories: Seq[GitRepository], teamName: String)

  case class RepositoryToTeam(repositoryName: String, teamName: String)

  def getCreatedAtDate(repos: Seq[GitRepository]) =
    repos.minBy(_.createdDate).createdDate

  def getLastActiveDate(repos: Seq[GitRepository]) =
    repos.maxBy(_.lastActiveDate).lastActiveDate

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
