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

import play.api.libs.concurrent.Execution.Implicits._
import uk.gov.hmrc.catalogue.github._
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, Team}

import scala.concurrent.Future

trait TeamsRepositoryDataSource {
  def getTeamRepoMapping: Future[List[Team]]
}

class GithubV3TeamsRepositoryDataSource(val gh: GithubV3ApiClient) extends TeamsRepositoryDataSource {
  def getTeamRepoMapping: Future[List[Team]] = {
    for {
      orgs <- gh.getOrganisations
      teams <- getOrgTeams(orgs)
      repos <- getTeamRepos(teams)
    } yield repos
  }

  private def getOrgTeams(orgs: List[GhOrganization]) =
    Future.sequence {
      orgs.par.map { gh.getTeamsForOrganisation }.toList
    }.map(_.flatten)

  def getTeamRepos(teams: List[GhTeam]) = {
    Future.sequence {
      teams.map { t =>
        gh.getReposForTeam(t).map(repos =>
          Team(t.name, repos.map(r => Repository(r.name, r.html_url))))
      }
    }
  }
}

class CompositeTeamsRepositoryDataSource(val dataSources: List[TeamsRepositoryDataSource]) extends TeamsRepositoryDataSource {
  override def getTeamRepoMapping =
    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      results.flatten.groupBy(_.teamName).map { group =>
        Team(group._1, group._2.flatMap(t => t.repositories))
      }.toList
    }
  }

