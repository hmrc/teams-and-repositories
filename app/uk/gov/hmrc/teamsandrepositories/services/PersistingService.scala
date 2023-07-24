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
import cats.data.EitherT

import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.connectors.{GithubConnector, ServiceConfigsConnector}
import uk.gov.hmrc.teamsandrepositories.models._
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class PersistingService @Inject()(
  persister               : RepositoriesPersistence,
  dataSource              : GithubV3RepositoryDataSource,
  configuration           : Configuration,
  serviceConfigsConnector : ServiceConfigsConnector,
  githubConnector         : GithubConnector
) {
  private val logger = Logger(this.getClass)

  def updateRepositories()(implicit ec: ExecutionContext): Future[Int] =
    for {
      frontendRoutes      <- serviceConfigsConnector.getFrontendServices()
      adminFrontendRoutes <- serviceConfigsConnector.getAdminFrontendServices()
      teams               <- dataSource.getTeams()
      teamRepos           <- teams.foldLeftM(List.empty[TeamRepositories]) { case (acc, team) =>
                               dataSource.getTeamRepositories(team).map(_ :: acc)
                             }
      reposWithTeams      =  teamRepos.foldLeft(Map.empty[String, GitRepository]) { case (acc, trs) =>
                               trs.repositories.foldLeft(acc) { case (acc, repo) =>
                                 val r = acc.getOrElse(repo.name, repo)
                                 acc + (r.name -> r.copy(teams = trs.teamName :: r.teams))
                               }
                             }
      allRepos            <- dataSource.getAllRepositoriesByName()
      orphanRepos         =  (allRepos -- reposWithTeams.keys).values
      reposToPersist      =  (reposWithTeams.values.toSeq ++ orphanRepos)
                               .map(defineServiceType(_, frontendRoutes = frontendRoutes, adminFrontendRoutes = adminFrontendRoutes))
                               .map(defineTag(_, adminFrontendRoutes = adminFrontendRoutes))
      _                   =  logger.info(s"found ${reposToPersist.length} repos")
      count               <- persister.updateRepos(reposToPersist)
    } yield count


  private val prototypeUrlTemplate =
    configuration.get[String]("url-templates.prototype")

  def updateRepository(name: String)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    for {
      rawRepo             <- EitherT.fromOptionF(
                               githubConnector.getRepo(name)
                             , s"not found on github"
                             )
      teams               <- EitherT.liftF(githubConnector.getTeams(name))
      frontendRoutes      <- EitherT
                               .liftF(serviceConfigsConnector.hasFrontendRoutes(name))
                               .map(x => if (x) Set(name) else Set.empty[String])
      adminFrontendRoutes <- EitherT
                               .liftF(serviceConfigsConnector.hasAdminFrontendRoutes(name))
                               .map(x => if (x) Set(name) else Set.empty[String])
      repo                <- EitherT
                               .pure[Future, String](rawRepo)
                               .map(_.toGitRepository(prototypeUrlTemplate))
                               .map(_.copy(teams = teams))
                               .map(defineServiceType(_, frontendRoutes = frontendRoutes, adminFrontendRoutes = adminFrontendRoutes))
                               .map(defineTag(_, adminFrontendRoutes = adminFrontendRoutes))
      _                   <- EitherT
                               .liftF(persister.upsertRepo(repo))
    } yield ()

  private def defineServiceType(repo: GitRepository, frontendRoutes: Set[String], adminFrontendRoutes: Set[String]): GitRepository = {
    repo.repoType match {
      case RepoType.Service
        if repo.serviceType.nonEmpty      => repo // serviceType already defined in repository.yaml
      case RepoType.Service
        if frontendRoutes.contains(repo.name)
        || adminFrontendRoutes.contains(repo.name)
        || repo.name.contains("frontend") => repo.copy(serviceType = Some(ServiceType.Frontend))
      case RepoType.Service               => repo.copy(serviceType = Some(ServiceType.Backend))
      case _                              => repo
    }
  }

  private def defineTag(repo: GitRepository, adminFrontendRoutes: Set[String]): GitRepository =
    repo.repoType match {
      case RepoType.Service
        if repo.tags.nonEmpty => repo // tags already defined in repository.yaml
      case RepoType.Service   => val newTags =
                                   Option.when(repo.name.contains("stub"))(Tag.Stub)                       ++
                                   Option.when(adminFrontendRoutes.contains(repo.name))(Tag.AdminFrontend) ++
                                   Option.when(repo.name.contains("admin-frontend"))(Tag.AdminFrontend)
                                 repo.copy(tags = Some(newTags.toSet))
      case _                  => repo
    }
}
