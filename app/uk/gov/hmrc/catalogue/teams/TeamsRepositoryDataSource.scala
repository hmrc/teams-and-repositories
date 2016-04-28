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

package uk.gov.hmrc.catalogue.teams

import play.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import uk.gov.hmrc.catalogue.config.CacheConfigProvider
import uk.gov.hmrc.catalogue.github._
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, TeamRepositories}

import scala.concurrent.Future


trait TeamsRepositoryDataSource {
  def getTeamRepoMapping: Future[List[TeamRepositories]]
}


class GithubV3TeamsRepositoryDataSource(val gh: GithubV3ApiClient, val isInternal: Boolean) extends TeamsRepositoryDataSource {
  self: GithubConfigProvider =>

  def getTeamRepoMapping: Future[List[TeamRepositories]] =
    gh.getOrganisations.flatMap { orgs =>
      Future.sequence(orgs.map(mapOrganisation)).map {
        _.flatten
      }
    }

  def mapOrganisation(organisation: GhOrganisation): Future[List[TeamRepositories]] =
    gh.getTeamsForOrganisation(organisation).flatMap { teams =>
      Future.sequence(for {
        team <- teams; if !githubConfig.hiddenTeams.contains(team.name)
      } yield mapTeam(organisation, team))
    }

  def mapTeam(organisation: GhOrganisation, team: GhTeam) =
    gh.getReposForTeam(team).flatMap { repos =>
      Future.sequence(for {
        repo <- repos; if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
      } yield mapRepository(organisation, repo)).map { repos =>
        TeamRepositories(team.name, repositories = repos)
      }
    }

  private def mapRepository(organisation: GhOrganisation, repo: GhRepository) =
    gh.containsAppFolder(organisation, repo).map {
      case (r, isMicroservice) => Repository(r.name, r.html_url, isInternal = this.isInternal, isMicroservice)
    }
}

class CompositeTeamsRepositoryDataSource(val dataSources: List[TeamsRepositoryDataSource]) extends TeamsRepositoryDataSource {
  override def getTeamRepoMapping =
    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      val flattened = results.flatten

      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
      flattened.groupBy(_.teamName).map { case (name, teams) =>
        TeamRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
      }.toList
    }
}

class CachingTeamsRepositoryDataSource(dataSource: TeamsRepositoryDataSource) extends TeamsRepositoryDataSource {
  self: CacheConfigProvider =>
  private var data: Future[List[TeamRepositories]] = dataSource.getTeamRepoMapping

  override def getTeamRepoMapping: Future[List[TeamRepositories]] = data

  def reload() = {
    Logger.info(s"Manual teams repository cache reload triggered")
    data = dataSource.getTeamRepoMapping
  }

  Logger.info(s"Initialising cache reload every ${cacheConfig.teamsCacheDuration}")
  Akka.system.scheduler.schedule(cacheConfig.teamsCacheDuration, cacheConfig.teamsCacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    data = dataSource.getTeamRepoMapping
  }
}