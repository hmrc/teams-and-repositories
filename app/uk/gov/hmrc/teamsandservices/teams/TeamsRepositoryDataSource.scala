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

package uk.gov.hmrc.teamsandservices.teams

import org.joda.time.DateTime
import play.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import uk.gov.hmrc.teamsandservices.CachedResult
import uk.gov.hmrc.teamsandservices.config.CacheConfigProvider
import uk.gov.hmrc.teamsandservices.github.GithubConfigProvider
import uk.gov.hmrc.teamsandservices.teams.ViewModels.{Repository, TeamRepositories}
import uk.gov.hmrc.githubclient.{GhOrganisation, GhRepository, GhTeam, GithubApiClient}

import scala.concurrent.Future

trait TeamsRepositoryDataSource {
  def getTeamRepoMapping: Future[Seq[TeamRepositories]]
}

class GithubV3TeamsRepositoryDataSource(val gh: GithubApiClient, val isInternal: Boolean) extends TeamsRepositoryDataSource {
  self : GithubConfigProvider =>

  def getTeamRepoMapping: Future[Seq[TeamRepositories]] =
    gh.getOrganisations.flatMap { orgs =>
      Future.sequence(orgs.map(mapOrganisation)).map {
        _.flatten
      }
    }

  def mapOrganisation(organisation: GhOrganisation): Future[List[TeamRepositories]] =
    gh.getTeamsForOrganisation(organisation.login).flatMap { teams =>
      Future.sequence(for {
        team <- teams; if !githubConfig.hiddenTeams.contains(team.name)
      } yield mapTeam(organisation, team))
    }

  def mapTeam(organisation: GhOrganisation, team: GhTeam) =
    gh.getReposForTeam(team.id).flatMap { repos =>
      Future.sequence(for {
        repo <- repos; if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
      } yield mapRepository(organisation, repo)).map { repos =>
        TeamRepositories(team.name, repositories = repos)
      }
    }

  private def mapRepository(organisation: GhOrganisation, repo: GhRepository) =
    gh.repoContainsFolder("app",repo.name, organisation.login).map(Repository(repo.name, repo.html_url, isInternal = this.isInternal, _))
}

class CompositeTeamsRepositoryDataSource(val dataSources: List[TeamsRepositoryDataSource]) extends TeamsRepositoryDataSource {
  override def getTeamRepoMapping: Future[Seq[TeamRepositories]] =
    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      val flattened = results.flatten

      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
      flattened.groupBy(_.teamName).map { case (name, teams) =>
        TeamRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
      }.toList
    }
}

class CachingTeamsRepositoryDataSource(dataSource: TeamsRepositoryDataSource, timeStamp: () => DateTime)  {
  self: CacheConfigProvider =>
  private var data: Future[CachedResult[Seq[TeamRepositories]]] = fromSource

  private def fromSource = {
    dataSource.getTeamRepoMapping.map { d => {
      val stamp = timeStamp()
      Logger.debug(s"Cache reloaded at $stamp")
      new CachedResult(d, stamp) }
    }
  }

  def getCachedTeamRepoMapping: Future[CachedResult[Seq[TeamRepositories]]] = data

  def reload() = {
    Logger.info(s"Manual teams repository cache reload triggered")
    data = fromSource
  }

  Logger.info(s"Initialising cache reload every ${cacheConfig.teamsCacheDuration}")
  Akka.system.scheduler.schedule(cacheConfig.teamsCacheDuration, cacheConfig.teamsCacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    data = fromSource
  }
}