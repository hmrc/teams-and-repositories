/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.services

import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.connectors.ServiceConfigsConnector
import uk.gov.hmrc.teamsandrepositories.models._
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class PersistingService @Inject()(
  persister               : RepositoriesPersistence,
  dataSource              : GithubV3RepositoryDataSource,
  configuration           : Configuration,
  serviceConfigsConnector : ServiceConfigsConnector
) {
  private val logger = Logger(this.getClass)

  def updateRepositories()(implicit ec: ExecutionContext): Future[Int] = {
    for {
      teams            <- dataSource.getTeams()
      teamRepos        <- teams.foldLeftM(List.empty[TeamRepositories]) { case (acc, team) =>
                            dataSource.getTeamRepositories(team).map(_ :: acc)
                          }
      reposWithTeams   =  teamRepos.foldLeft(Map.empty[String, GitRepository]) { case (acc, trs) =>
                            trs.repositories.foldLeft(acc) { case (acc, repo) =>
                              val r = acc.getOrElse(repo.name, repo)
                              acc + (r.name -> r.copy(teams = trs.teamName :: r.teams))
                            }
                          }
      allRepos         <- dataSource.getAllRepositoriesByName()
      orphanRepos      =  (allRepos -- reposWithTeams.keys).values
      reposToSeq       =  reposWithTeams.values.toSeq ++ orphanRepos
      frontendServices <- serviceConfigsConnector.getFrontendServices()
      reposToPersist   =  reposToSeq.map(r => defineServiceType(r, frontendServices))
      _                =  logger.info(s"found ${reposToPersist.length} repos")
      count            <- persister.updateRepos(reposToPersist) // TODO double check that repository yaml exists before cleaning up // TODO audit deletions
    } yield count
  }

  def defineServiceType(repo: GitRepository, frontendServices: Set[String]): GitRepository = {
    repo.repoType match {
      case RepoType.Service if frontendServices.contains(repo.name) => repo.copy(serviceType = Some(FrontendService))
      case RepoType.Service                                         => repo.copy(serviceType = Some(BackendService))
      case _                                                        => repo
    }
  }
}
