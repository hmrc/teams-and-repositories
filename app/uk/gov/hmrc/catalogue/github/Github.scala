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

import java.io.File

import uk.gov.hmrc.catalogue.github.Model.{Repository, Team}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CompositeDataSource extends CatalogueDataSource {
  val dataSource = List(GithubEnterpriseDataSource, GithubOpenDataSource)

  override def getTeamRepoMapping: Future[List[Team]] = ???
}

trait CatalogueDataSource {
  def getTeamRepoMapping: Future[List[Team]]
}

object GithubOpenDataSource extends CatalogueDataSource {
  override def getTeamRepoMapping: Future[List[Team]] = ???
}

object GithubEnterpriseDataSource extends GithubEnterpriseDataSource {
  val gh: GithubHttp = new GithubHttp with GithubEnterpriseApiEndpoints with GithubCredentials
}

trait GithubCredentials extends CredentialsFinder {
  val cred: ServiceCredentials = new File(System.getProperty("user.home"), ".github").listFiles()
    .flatMap { c => findGithubCredsInFile(c.toPath) }.head
}

trait GithubEnterpriseDataSource extends CatalogueDataSource {
  def gh: GithubHttp

  val teamsToIgnore: Set[String] = Set.empty
  val repoPhrasesToIgnore: Set[String] = Set.empty

  def getTeamRepoMapping: Future[List[Team]] = {
    val orgs = gh.getOrganisations
    val teams = getOrgTeams(orgs)

    teams.flatMap {
      teams => Future.sequence {
        teams.par.map {
          t => gh.getReposForTeam(t).map(
            repos => Team(t.name, repos.map(
              y => Repository(y.name, y.html_url))))
        }.toList
      }
    }
  }

  private def getOrgTeams(orgsF: Future[List[GhOrganization]]): Future[List[GhTeam]] = {
    orgsF.flatMap {
      orgs => Future.sequence {
        orgs.par.map { gh.getTeamsForOrganisation }.toList
      }
    }.map(_.flatten)
  }

}
