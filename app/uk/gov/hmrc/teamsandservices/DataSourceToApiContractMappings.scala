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

object DataSourceToApiContractMappings {

  implicit def RepositoryWrapper(repository: Repository): RepositoryWrapper = new RepositoryWrapper(repository)
  implicit def CachedRepositoryDataWrapper(
    cachedResult: CachedResult[Seq[TeamRepositories]]): CachedTeamRepositoryWrapper
      = new CachedTeamRepositoryWrapper(cachedResult)

  class CachedTeamRepositoryWrapper(cachedTeamRepositories: CachedResult[Seq[TeamRepositories]]) {

    def asTeamsList = cachedTeamRepositories.map { teams => teams.map(_.teamName) }

    def asServicesList(ciUrlTemplates: UrlTemplates) =
      cachedTeamRepositories.map { data =>
        repositoryTeams(data)
          .groupBy(_.repo)
          .flatMap { case (repo, t) => repo.asService(t.map(_.teamName), ciUrlTemplates) }
          .toSeq
          .sortBy(_.name) }

    def asTeamServices(teamName: String, ciUrlTemplates: UrlTemplates) =
      asServicesList(ciUrlTemplates).map { services =>
        services.filter(_.teamNames.contains(URLDecoder.decode(teamName, "UTF-8"))) }

    private case class RepositoryTeam(repo: Repository, teamName: String)
    private def repositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoryTeam] =
      for {
        team <- data
        repo <- team.repositories
      } yield RepositoryTeam(repo, team.teamName)
  }

  class RepositoryWrapper(repository: Repository) {
    def asService(teamNames: Seq[String], ciUrlTemplates: UrlTemplates): Option[Service] =
      if (!repository.deployable) None
      else Some(
        Service(
          repository.name,
          teamNames,
          Link("github", repository.url),
          buildCiUrls(repository, ciUrlTemplates)))

    private def buildUrls(templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.url(repository.name))).toList

    private def buildCiUrls(repository: Repository, urlTemplates: UrlTemplates): List[Link] =
      repository.isInternal match {
        case true => buildUrls(urlTemplates.ciClosed)
        case false => buildUrls(urlTemplates.ciOpen) }
  }
}
