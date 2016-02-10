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

import uk.gov.hmrc.catalogue.github.Model.{GhRepository, GhTeam, GhOrganization}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Github(val gh: GithubHttp, teamsToIgnore: Set[String] = Set.empty, repoPhrasesToIgnore: Set[String] = Set.empty) {


  //  def getTeamRepoMapping(
  //                          org: GhOrganization,
  //                          teamFilter: (GhTeam => Boolean) = _ => true,
  //                          repoFilter: (GhRepository => Boolean) = _ => true
  //                          ): Future[Map[GhTeam, List[GhRepository]]] = {
  //    def nonIgnoredTeams(team: GhTeam) = !teamsToIgnore.contains(team.name)
  //
  //    def nonIgnoredRepos(repo: GhRepository) = {
  //      !repoPhrasesToIgnore.exists(r => repo.name.contains(r))
  //    }
  //
  //    gh.get[List[GhTeam]](teamsUrl(org.login)).filterL(teamFilter).filterL(nonIgnoredTeams).flatMapL { team =>
  //      gh.get[List[GhRepository]](teamReposUrl(team.id)).filterL(repoFilter).filterL(nonIgnoredRepos).map { rs => List(team -> rs) }
  //    }.map(_.toMap)
  //  }


  def getTeamRepoMapping: Future[Map[GhTeam, List[String]]] = {


    ???

  }


  //  def getRepoMetaData(
  //                       orgFilter: (GhOrganization => Boolean) = _ => true,
  //                       teamFilter: (GhTeam => Boolean) = _ => true,
  //                       repoFilter: (GhRepository => Boolean) = _ => true
  //                       ): Future[List[(GhOrganization, List[(GhTeam, List[Repository])])]] = {
  //    gh.get[List[GhOrganization]](orgUrl).filterL(orgFilter).flatMapL { org =>
  //      getTeamRepos(org, teamFilter, repoFilter).map { rs => println(org -> rs); List(org -> rs) }
  //    }
  //  }

  //  def getTeamRepos(
  //                    org: GhOrganization,
  //                    teamFilter: (GhTeam => Boolean) = _ => true,
  //                    repoFilter: (GhRepository => Boolean) = _ => true
  //                    ): Future[List[(GhTeam, List[String])]] = {
  //    val teamRepoMappingF: Future[List[(GhTeam, List[GhRepository])]] = getTeamRepoMapping(org, teamFilter, repoFilter).map(_.toList)
  //
  //    val x: Future[List[(GhTeam, String)]] = teamRepoMappingF.flatMapL { case (team, repoList) =>
  //      println(s"repoList = ${org.login} ${team.name} " + repoList.size)
  //      val z: List[Future[Option[(GhTeam, Repository)]]] = repoList.filter(repoFilter).map { r =>
  //        lastCommit/*WithRetry*/(org, r).map { lastCommit => lastCommit
  //          .map(c => Repository(org.login, team.name, r.name, c.date.toLocalDate, r.ssh_url))
  //          .map { rm => team -> rm }
  //        }
  //      }
  //
  //      Future.sequence(z).filterL(_.isDefined).map(_.flatten)
  //    }
  //
  //    x map groupOnFirstTuple
  //  }

  def collaboratorsUrl(org: String, repo: String): String = {
    s"https://${gh.host}/api/v3/repos/$org/$repo/collaborators"
  }

  def teamsUrl(org: String): String = {
    s"https://${gh.host}/api/v3/orgs/$org/teams?per_page=100"
  }

  def teamReposUrl(teamId: Long): String = {
    s"https://${gh.host}/api/v3/teams/$teamId/repos?per_page=100"
  }

  def commitsUrl(org: String, repo: String): String = {
    s"https://${gh.host}/api/v3/repos/$org/$repo/commits"
  }

  def orgUrl: String = {
    s"https://${gh.host}/api/v3/user/orgs"
  }

  def reposUrl(org: GhOrganization): String = {
    s"https://${gh.host}/api/v3/orgs/${org.login}/repos?per_page=100"
  }

}
