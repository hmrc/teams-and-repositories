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

package uk.gov.hmrc.teamsandservices

import java.net.URLDecoder
import uk.gov.hmrc.teamsandservices.config.{UrlTemplate, UrlTemplates}

object TeamRepositoryWrapper {

  implicit class TeamRepositoryWrapper(teamRepos: Seq[TeamRepositories]) {

    def asTeamNameList = teamRepos.map(_.teamName)

    def asServiceNameList = {

      val repoNames = for {
        d <- teamRepos
        r <- extractRepositoriesForServices(d.repositories)
      } yield r.name

      repoNames
        .distinct
        .sortBy(_.toUpperCase)
    }

    def findService(serviceName: String, ciUrlTemplates: UrlTemplates): Option[Service] = {
      val decodedServiceName = URLDecoder.decode(serviceName, "UTF-8")

      asServicesList(ciUrlTemplates)
        .find(_.name == decodedServiceName)
    }

    def asServicesList(ciUrlTemplates: UrlTemplates): Seq[Service] =
      repositoryTeams(teamRepos)
        .groupBy(_.repositories)
        .flatMap { case (repositories, t) => repoGroupToService(repositories, t.map(_.teamName), ciUrlTemplates) }
        .toSeq
        .sortBy(_.name.toUpperCase)


    def asTeamServiceNameList(teamName: String): Option[List[String]] = {
      val decodedTeamName = URLDecoder.decode(teamName, "UTF-8")
      teamRepos.find(_.teamName == decodedTeamName).map { t =>
        extractRepositoriesForServices(t.repositories).map(_.name)
          .distinct
          .sortBy(_.toUpperCase)
      }
    }

    private case class RepositoryTeam(repositories: Seq[Repository], teamName: String)

    private def repositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoryTeam] =
      for {
        team <- data
        repositories <- team.repositories.groupBy(_.name).values
      } yield RepositoryTeam(repositories, team.teamName)
  }

  def repoGroupToService(repositories: Seq[Repository], teamNames: Seq[String], urlTemplates: UrlTemplates): Option[Service] = {

    val primaryRepository = extractRepositoriesForServices(repositories).sortBy(_.isInternal).headOption

    def buildUrls(repo: Repository, templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.displayName, t.url(repo.name))).toList

    def buildEnvironmentUrls(repository: Repository, urlTemplates: UrlTemplates): Seq[Environment] = {
      urlTemplates.environments.map { case (name, tps) =>
        val links = tps.map { tp => Link(tp.name, tp.displayName, tp.url(repository.name)) }
        Environment(name, links)
      }.toSeq
    }

    def buildCiUrls(repository: Repository, urlTemplates: UrlTemplates): List[Link] =
      repository.isInternal match {
        case true => buildUrls(repository, urlTemplates.ciClosed)
        case false => buildUrls(repository, urlTemplates.ciOpen)
      }

    def githubName(isInternal: Boolean) = if (isInternal) "github-enterprise" else "github-com"
    def githubDisplayName(isInternal: Boolean) = if (isInternal) "Github Enterprise" else "GitHub.com"

    primaryRepository.map { repo =>
      Service(
        repo.name,
        teamNames,
        repositories.map { repo =>
          Link(
            githubName(repo.isInternal),
            githubDisplayName(repo.isInternal),
            repo.url)
        },
        buildCiUrls(repo, urlTemplates),
        buildEnvironmentUrls(repo, urlTemplates))

    }

  }

  def extractRepositoriesForServices(repositories: Seq[Repository]): List[Repository] = {
    repositories
      .groupBy(_.name)
      .filter { case (name, repos) => repos.exists(x => x.repoType == RepoType.Deployable) }
      .flatMap(_._2).filter(!_.name.contains("prototype")).toList
  }

}
