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
    def asTeamsList = {
      cachedTeamRepositories.map { teams =>
        teams.map(_.teamName)
      }
    }

    def asServicesList(ciUrlTemplates: UrlTemplates) = {
      cachedTeamRepositories.map { data => (
          for {
            team <- data
            repo <- team.repositories
          } yield repo.asService(team.teamName, ciUrlTemplates)
        ).flatten.sortBy(_.name)
      }
    }

    def asSingleTeam[T](teamName: String, ciUrlTemplates: UrlTemplates)(transform: CachedResult[List[Service]] => T): Option[T] = {
      cachedTeamRepositories.data.find(
        _.teamName == URLDecoder.decode(teamName, "UTF-8")
      ) map { team =>
        transform(cachedTeamRepositories map { _ =>
          team.repositories.flatMap(repository => repository.asService(team.teamName, ciUrlTemplates))
        })
      }
    }
  }

  class RepositoryWrapper(repository: Repository) {
    def asService(teamName: String, ciUrlTemplates: UrlTemplates): Option[Service] = {
      if (!repository.deployable) None
      else Some(
        Service(
          repository.name,
          teamName,
          Link("github", repository.url),
          buildCiUrls(repository, ciUrlTemplates)
        )
      )
    }

    private def buildCiUrls(repository: Repository, urlTemplates: UrlTemplates): List[Link] = {
      def buildUrls(templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.url(repository.name))).toList

      repository.isInternal match {
        case true => buildUrls(urlTemplates.ciClosed)
        case false => buildUrls(urlTemplates.ciOpen)
      }
    }
  }
}
