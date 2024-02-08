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
import cats.data.{EitherT, OptionT}
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.connectors.ServiceConfigsConnector
import uk.gov.hmrc.teamsandrepositories.models._
import uk.gov.hmrc.teamsandrepositories.persistence.{RepositoriesPersistence, TeamSummaryPersistence, TestRepoRelationshipsPersistence}
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class PersistingService @Inject()(
  repositoriesPersistence : RepositoriesPersistence,
  teamSummaryPersistence  : TeamSummaryPersistence,
  relationshipsPersistence: TestRepoRelationshipsPersistence,
  dataSource              : GithubV3RepositoryDataSource,
  configuration           : Configuration,
  serviceConfigsConnector : ServiceConfigsConnector,
) {
  private val logger = Logger(this.getClass)

  private def updateTeams(gitRepos: List[GitRepository], teamRepos: List[TeamRepositories])(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      count <- teamSummaryPersistence.updateTeamSummaries(
                 TeamSummary.createTeamSummaries(gitRepos) ++
                   teamRepos.collect {
                     // we also store teams with no repos
                     case trs if trs.repositories.isEmpty => TeamSummary(trs.teamName, None, Seq.empty)
                   }
               )
      _     =  logger.info(s"Persisted: $count Teams")
    } yield ()
  }

  private def updateRepositories(reposWithTeams: Map[String, GitRepository])(implicit ec: ExecutionContext): Future[Unit] =
    for {
      frontendRoutes      <- serviceConfigsConnector.getFrontendServices()
      adminFrontendRoutes <- serviceConfigsConnector.getAdminFrontendServices()
      allRepos            <- dataSource.getAllRepositoriesByName()
      orphanRepos         =  (allRepos -- reposWithTeams.keys).values
      reposToPersist      =  (reposWithTeams.values.toSeq ++ orphanRepos)
                               .map(defineServiceType(_, frontendRoutes = frontendRoutes, adminFrontendRoutes = adminFrontendRoutes))
                               .map(defineTag(_, adminFrontendRoutes = adminFrontendRoutes))
      _                   =  logger.info(s"found ${reposToPersist.length} repos")
      count               <- repositoriesPersistence.updateRepos(reposToPersist)
      _                   =  logger.info(s"Persisted: $count repos")
      _                   <- reposToPersist.toList.traverse(updateTestRepoRelationships)
    } yield ()

  def updateTeamsAndRepositories()(implicit ec: ExecutionContext): Future[Unit] =
    for {
      teams          <- dataSource.getTeams()
      teamRepos      <- teams.foldLeftM(List.empty[TeamRepositories]) { case (acc, team) =>
                          dataSource.getTeamRepositories(team).map(_ :: acc)
                       }
      reposWithTeams =  teamRepos.foldLeft(Map.empty[String, GitRepository]) { case (acc, trs) =>
                          trs.repositories.foldLeft(acc) { case (acc, repo) =>
                            val r = acc.getOrElse(repo.name, repo)
                            acc + (r.name -> r.copy(teams = trs.teamName :: r.teams))
                          }
                        }.view.mapValues(repo =>
                          repo.copy(owningTeams = if (repo.owningTeams.isEmpty) repo.teams else repo.owningTeams)
                        )
      _             <- updateTeams(reposWithTeams.values.toList, teamRepos)
      _             <- updateRepositories(reposWithTeams.toMap)
    } yield ()

  def updateRepository(repoName: String)(implicit ec: ExecutionContext): EitherT[Future, String, Unit] =
    for {
      rawRepo             <- EitherT.fromOptionF(
                               dataSource.getRepo(repoName)
                             , s"not found on github"
                             )
      teams               <- EitherT.liftF(dataSource.getTeams(repoName))
      frontendRoutes      <- EitherT
                               .liftF(serviceConfigsConnector.hasFrontendRoutes(repoName))
                               .map(x => if (x) Set(repoName) else Set.empty[String])
      adminFrontendRoutes <- EitherT
                               .liftF(serviceConfigsConnector.hasAdminFrontendRoutes(repoName))
                               .map(x => if (x) Set(repoName) else Set.empty[String])
      repo                <- EitherT
                               .pure[Future, String](rawRepo)
                               .map(repo =>
                                  repo.copy(
                                    teams       = teams,
                                    owningTeams = if (repo.owningTeams.isEmpty) teams else repo.owningTeams
                                  )
                                )
                               .map(defineServiceType(_, frontendRoutes = frontendRoutes, adminFrontendRoutes = adminFrontendRoutes))
                               .map(defineTag(_, adminFrontendRoutes = adminFrontendRoutes))
      _                   <- EitherT
                               .liftF(repositoriesPersistence.putRepo(repo))
      _                   <- EitherT
                               .liftF(updateTestRepoRelationships(rawRepo))
    } yield ()

  def repositoryArchived(repoName: String): Future[Unit] =
    repositoriesPersistence.archiveRepo(repoName)

  def repositoryDeleted(repoName: String): Future[Unit] =
    repositoriesPersistence.deleteRepo(repoName)

  private def updateTestRepoRelationships(repo: GitRepository)(implicit ec: ExecutionContext): Future[Unit] = {
    import uk.gov.hmrc.teamsandrepositories.util.YamlMap

    val testRepos: OptionT[Future, List[String]] = for {
      yamlText       <- OptionT.fromOption[Future](repo.repositoryYamlText)
      yamlMap        <- OptionT.fromOption[Future](YamlMap.parse(yamlText).toOption)
      testReposArray <- OptionT.fromOption[Future](yamlMap.getArray("test-repositories"))
    } yield testReposArray

    testRepos.value.flatMap {
      case Some(repos) => relationshipsPersistence.putRelationships(repo.name, repos.map(TestRepoRelationship(_, repo.name)))
      case None        => Future.unit
    }
  }

  private def defineServiceType(repo: GitRepository, frontendRoutes: Set[String], adminFrontendRoutes: Set[String]): GitRepository =
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
