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

  implicit def RepositorySeqWrapper(repositories: Seq[Repository]): RepositorySeqWrapper = new RepositorySeqWrapper(repositories)
  implicit def CachedRepositoryDataWrapper(
    cachedResult: CachedResult[Seq[TeamRepositories]]): CachedTeamRepositoryWrapper
      = new CachedTeamRepositoryWrapper(cachedResult)

  class CachedTeamRepositoryWrapper(cachedTeamRepositories: CachedResult[Seq[TeamRepositories]]) {

    def asTeamsList = cachedTeamRepositories.map { teams => teams.map(_.teamName) }

    def asServicesList(ciUrlTemplates: UrlTemplates): CachedResult[Seq[Service]] =
      cachedTeamRepositories.map { data =>
        repositoryTeams(data)
          .groupBy(_.repositories)
          .flatMap { case (repositories, t) => repositories.asService(t.map(_.teamName), ciUrlTemplates) }
          .toSeq
          .sortBy(_.name.toUpperCase)
        }

    def asTeamServices(teamName: String, ciUrlTemplates: UrlTemplates) = {
      val decodedTeamName = URLDecoder.decode(teamName, "UTF-8")
      cachedTeamRepositories.map { data =>
        data.find(_.teamName == decodedTeamName).map { t =>
            asServicesList(ciUrlTemplates).map { services =>
              services.filter(_.teamNames.contains(t.teamName)) }}
      }
    }

    private case class RepositoryTeam(repositories: Seq[Repository], teamName: String)
    private def repositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoryTeam] =
      for {
        team <- data
        repositories <- team.repositories.groupBy(_.name).values
      } yield RepositoryTeam(repositories, team.teamName)
  }

  class RepositorySeqWrapper(repositories: Seq[Repository]) {
    val primaryRepository = repositories.sortBy(_.isInternal).head

    def asService(teamNames: Seq[String], urlTemplates: UrlTemplates): Option[Service] =
      if (!primaryRepository.deployable) None
      else Some(
        Service(
          primaryRepository.name,
          teamNames,
          repositories.map { repo =>
            Link(if (repo.isInternal) "github" else "github-open", repo.url)
          },
          buildCiUrls(primaryRepository, urlTemplates),
          buildEnvironmentUrls(primaryRepository, urlTemplates)))

    private def buildUrls(templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.url(primaryRepository.name))).toList

    private def buildEnvironmentUrls(repository: Repository, urlTemplates: UrlTemplates): Seq[Environment] ={
      urlTemplates.environments.map { case(name, tps) =>
        val links = tps.map { tp => Link(tp.name, tp.url(repository.name)) }
        Environment(name, links)
      }.toSeq
    }

    private def buildCiUrls(repository: Repository, urlTemplates: UrlTemplates): List[Link] =
      repository.isInternal match {
        case true => buildUrls(urlTemplates.ciClosed)
        case false => buildUrls(urlTemplates.ciOpen) }
  }
}
