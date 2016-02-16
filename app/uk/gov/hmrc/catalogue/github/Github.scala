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

package uk.gov.hmrc.catalogue.github

import uk.gov.hmrc.catalogue.github.Model.{Repository, Team}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CompositeDataSource extends CatalogueDataSource {
  val dataSource = List(GithubEnterprise, GithubOpen)

  override def getTeamRepoMapping: Future[List[Team]] = ???
}


trait CatalogueDataSource {
  def getTeamRepoMapping: Future[List[Team]]
}

object GithubOpen extends CatalogueDataSource {
  override def getTeamRepoMapping: Future[List[Team]] = ???
}

object GithubEnterprise extends GithubEnterprise {
  val gh: GithubHttp = GithubHttp
}

trait GithubEnterprise extends CatalogueDataSource {
  def gh: GithubHttp

  val teamsToIgnore: Set[String] = Set.empty
  val repoPhrasesToIgnore: Set[String] = Set.empty

  def getTeamRepoMapping: Future[List[Team]] = {
    val orgs = gh.get[List[GhOrganization]](orgUrl)
    val teams = getOrgTeams(orgs)

    teams.flatMap { teams =>
      Future.sequence {
        teams.par.map { t => gh.get[List[GhRepository]](teamReposUrl(t.id)).map(repos => Team(t.name, repos.map(y => Repository(y.name, y.html_url)))) }.toList
      }
    }
  }

  private def getOrgTeams(orgsF: Future[List[GhOrganization]]): Future[List[GhTeam]] = {
    orgsF.flatMap { orgs =>
      Future.sequence {
        orgs.par.map { x =>
          gh.get[List[GhTeam]](teamsUrl(x.login))
        }.toList
      }
    }.map(_.flatten)
  }


  def teamsUrl(org: String): String = {
    s"https://${gh.host}/api/v3/orgs/$org/teams?per_page=100"
  }

  def teamReposUrl(teamId: Long): String = {
    s"https://${gh.host}/api/v3/teams/$teamId/repos?per_page=100"
  }


  def orgUrl: String = {
    s"https://${gh.host}/api/v3/user/orgs"
  }

}
