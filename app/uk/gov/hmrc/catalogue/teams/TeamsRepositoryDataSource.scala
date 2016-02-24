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

import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current

import uk.gov.hmrc.catalogue.config.CacheConfigProvider
import uk.gov.hmrc.catalogue.github._
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, Team}

import scala.concurrent.Future
import scala.concurrent.duration._

trait TeamsRepositoryDataSource {
  def getTeamRepoMapping: Future[List[Team]]
}

trait TeamsRepositoryDataSourceProvider {
  def dataSource: TeamsRepositoryDataSource
}

class GithubV3TeamsRepositoryDataSource(val gh: GithubV3ApiClient) extends TeamsRepositoryDataSource {
  self: GithubConfigProvider =>

  def getTeamRepoMapping: Future[List[Team]] = {
    for {
      orgs <- gh.getOrganisations
      teams <- getOrgTeams(orgs)
      repos <- getTeamRepos(teams)
    } yield repos
  }

  private def getOrgTeams(orgs: List[GhOrganization]) =
    Future.sequence {
      orgs.map { gh.getTeamsForOrganisation }
    }.map(_.flatten)

  private def getTeamRepos(teams: List[GhTeam]) =
    Future.sequence {
      teams.map { team =>
        for (repos <- gh.getReposForTeam(team)) yield Team(team.name, mapRepositories(repos))
      }
    }

  private def mapRepositories(repos: List[GhRepository]) =
    for (repo <- repos; if !repo.fork && !githubConfig.repositoryBlacklist.contains(repo.name)) yield Repository(repo.name, repo.html_url)
}

class CompositeTeamsRepositoryDataSource(val dataSources: List[TeamsRepositoryDataSource]) extends TeamsRepositoryDataSource {
  override def getTeamRepoMapping =
    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      results.flatten.groupBy(_.teamName).map { case (name, teams) =>
        Team(name, teams.flatMap(t => t.repositories))
      }.toList
    }
  }

class CachingTeamsRepositoryDataSource(dataSource: TeamsRepositoryDataSource) extends TeamsRepositoryDataSource {
  self: CacheConfigProvider  =>

  var data: Option[Future[List[Team]]] = None

  override def getTeamRepoMapping: Future[List[Team]] = {
    data.getOrElse(Future.successful(List()))
  }

  Akka.system.scheduler.schedule(0 seconds, cacheConfig.teamsCacheDuration) {
    data = Some(dataSource.getTeamRepoMapping)
  }
}