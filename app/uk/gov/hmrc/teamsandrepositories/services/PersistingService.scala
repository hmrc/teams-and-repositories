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

import cats.implicits.*
import cats.data.{EitherT, OptionT}
import com.google.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.teamsandrepositories.connectors.{GhRepository, GithubConnector, ServiceConfigsConnector}
import uk.gov.hmrc.teamsandrepositories.models.*
import uk.gov.hmrc.teamsandrepositories.persistence.{DeletedRepositoriesPersistence, RepositoriesPersistence, TeamSummaryPersistence, TestRepoRelationshipsPersistence}
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship
import uk.gov.hmrc.mongo.MongoUtils.DuplicateKey

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class PersistingService @Inject()(
  repositoriesPersistence       : RepositoriesPersistence,
  deletedRepositoriesPersistence: DeletedRepositoriesPersistence,
  teamSummaryPersistence        : TeamSummaryPersistence,
  relationshipsPersistence      : TestRepoRelationshipsPersistence,
  configuration                 : Configuration,
  serviceConfigsConnector       : ServiceConfigsConnector,
  githubConnector               : GithubConnector
) extends Logging:

  private val hiddenTeams              = configuration.get[Seq[String]]("hidden.teams").toSet
  private val servicesBuiltOffPlatform = configuration.get[Seq[String]]("built-off-platform").toSet

  private def updateRepositories(reposWithTeams: Seq[GitRepository])(using ExecutionContext): Future[Unit] =
    for
      frontendServices           <- serviceConfigsConnector.getFrontendServices()
      adminFrontendServices      <- serviceConfigsConnector.getAdminFrontendServices()
      ghRepos                    <- githubConnector.getRepos
      allRepos                   =  ghRepos.map(r => r.name -> r.toGitRepository).toMap
      orphanRepos                =  (allRepos -- reposWithTeams.map(_.name).toSet).values
      toPersistRepos             =  (reposWithTeams ++ orphanRepos)
                                      .map(r => defineServiceType(r, isFrontend = frontendServices.contains(r.name), isAdmin = adminFrontendServices.contains(r.name)))
                                      .map(r => defineTag(r, isAdmin = adminFrontendServices.contains(r.name), servicesBuiltOffPlatform, ghRepos.find(_.name == r.name)))
      toPersistReposNames        =  toPersistRepos.map(_.name).toSet

      _                          =  logger.info(s"found ${toPersistRepos.length} repos")
      updateCount                <- repositoriesPersistence.putRepos(toPersistRepos)

      alreadyDeletedReposNames   <- deletedRepositoriesPersistence.find().map(_.map(_.name).toSet)
      recreatedRepos             =  (alreadyDeletedReposNames intersect toPersistReposNames).toSeq
      _                          =  if   recreatedRepos.nonEmpty
                                    then logger.info(s"About to recreate ${recreatedRepos.length} previously deleted repos: ${recreatedRepos.mkString(", ")}")
                                    else ()
      recreateCount              <- deletedRepositoriesPersistence.deleteRepos(recreatedRepos)

      alreadyPersistedReposNames <- repositoriesPersistence.find().map(_.map(_.name).toSet)
      deletedRepos               =  (alreadyPersistedReposNames -- toPersistReposNames).toSeq
      _                          =  if   deletedRepos.nonEmpty
                                    then logger.info(s"About to remove ${deletedRepos.length} deleted repos: ${deletedRepos.mkString(", ")}")
                                    else ()
      deletedCount               <- deletedRepos.foldLeftM(0) { case (acc, repo) => deleteRepository(repo).map(_ => acc + 1) }

      _                          =  logger.info(s"Updated: $updateCount repos. Deleted: $deletedCount repos. Recreated: $recreateCount previously deleted repos.")
      _                          <- toPersistRepos.toList.traverse(updateTestRepoRelationships)
    yield ()

  def updateTeamsAndRepositories()(using ExecutionContext): Future[Unit] =
    for
      gitHubTeams    <- githubConnector.getTeams.map(_.filterNot(team => hiddenTeams.contains(team.name)))
      teamReposMap   <- gitHubTeams.foldLeftM(Map.empty[String, List[GitRepository]]): (acc, team) =>
                          githubConnector
                            .getReposForTeam(team)
                            .map: ghRepos =>
                              acc ++ Map(team.name -> ghRepos.map(_.toGitRepository).sortBy(_.name))
      repos          =  teamReposMap.values.flatten.toList.distinctBy(_.name)
      teamsForRepo   =  teamReposMap.toList.flatMap((teamName, repos) => repos.map(r => (r.name, teamName))).groupMap(_._1)(_._2)
      reposWithTeams =  repos.map: repo =>
                          val teams = teamsForRepo(repo.name).toList.sorted
                          repo.copy(
                            teams       = teams,
                            owningTeams = if repo.owningTeams.isEmpty then teams else repo.owningTeams
                          )
      teamSummaries  =  gitHubTeams.map(team => TeamSummary(
                          teamName = team.name,
                          gitRepos = reposWithTeams.filter(repo => repo.owningTeams.contains(team.name) && !repo.isArchived)
                        ))
      count          <- teamSummaryPersistence.updateTeamSummaries(teamSummaries)
      _              =  logger.info(s"Persisted: $count Teams")
      _              <- updateRepositories(reposWithTeams)
    yield ()

  def addTeam(team: TeamSummary): Future[Unit] =
    teamSummaryPersistence.add(team)
    
  def updateRepository(repoName: String)(using ExecutionContext): EitherT[Future, String, Unit] =
    for
      ghRepo                 <- EitherT.fromOptionF(
                                  githubConnector.getRepo(repoName)
                                , s"not found on github"
                                )
      rawRepo                =  ghRepo.toGitRepository
      teams                  <- EitherT.liftF(githubConnector.getTeams(repoName).map(_.filterNot(team => hiddenTeams.contains(team))))
      isFrontendService      <- EitherT
                                 .liftF(serviceConfigsConnector.hasFrontendRoutes(repoName))
      isAdminFrontendService <- EitherT
                                 .liftF(serviceConfigsConnector.hasAdminFrontendRoutes(repoName))
      repo                   <- EitherT
                                  .pure[Future, String](rawRepo)
                                  .map: repo =>
                                     repo.copy(
                                       teams       = teams,
                                       owningTeams = if repo.owningTeams.isEmpty then teams else repo.owningTeams
                                     )
                                  .map(defineServiceType(_, isFrontend = isFrontendService, isAdmin = isAdminFrontendService))
                                  .map(r => defineTag(r, isAdmin = isAdminFrontendService, servicesBuiltOffPlatform, Some(ghRepo)))
      _                      <- EitherT
                                  .liftF(deletedRepositoriesPersistence.deleteRepos(Seq(repo.name)))
      _                      <- EitherT
                                  .liftF(repositoriesPersistence.putRepo(repo))
      _                      <- EitherT
                                  .liftF(updateTestRepoRelationships(rawRepo))
    yield ()

  def archiveRepository(repoName: String): Future[Unit] =
    repositoriesPersistence.archiveRepo(repoName)

  def deleteRepository(repoName: String)(using ExecutionContext): Future[Unit] =
    repositoriesPersistence
      .findRepo(repoName)
      .flatMap:
        case Some(repo) => for {
                             _ <- deletedRepositoriesPersistence.putRepo(DeletedGitRepository.fromGitRepository(repo, Instant.now()))
                             _ <- repositoriesPersistence.deleteRepo(repoName)
                           } yield ()
        case None       => deletedRepositoriesPersistence.putRepo(DeletedGitRepository(repoName, Instant.now()))
      .recover:
        case DuplicateKey(_) => logger.info(s"repo: $repoName - already stored in deleted-repositories collection")

  private def updateTestRepoRelationships(repo: GitRepository)(using ExecutionContext): Future[Unit] =
    import uk.gov.hmrc.teamsandrepositories.util.YamlMap

    val testRepos: OptionT[Future, List[String]] =
      for
        yamlText       <- OptionT.fromOption[Future](repo.repositoryYamlText)
        yamlMap        <- OptionT.fromOption[Future](YamlMap.parse(yamlText).toOption)
        testReposArray <- OptionT.fromOption[Future](yamlMap.getArray("test-repositories"))
      yield testReposArray

    testRepos.value.flatMap:
      case Some(repos) => relationshipsPersistence.putRelationships(repo.name, repos.map(TestRepoRelationship(_, repo.name)))
      case None        => Future.unit

  private def defineServiceType(
   repo      : GitRepository,
   isFrontend: Boolean,
   isAdmin   : Boolean
  ): GitRepository =
    repo.repoType match
      case RepoType.Service
        if repo.serviceType.nonEmpty      => repo // serviceType already defined in repository.yaml
      case RepoType.Service
        if isFrontend
        || isAdmin
        || repo.name.contains("frontend") => repo.copy(serviceType = Some(ServiceType.Frontend))
      case RepoType.Service               => repo.copy(serviceType = Some(ServiceType.Backend))
      case _                              => repo

  private def defineTag(
    repo                    : GitRepository,
    isAdmin                 : Boolean,
    servicesBuiltOffPlatform: Set[String],
    ghRepo                  : Option[GhRepository]
  ): GitRepository =
    repo.repoType match
      case RepoType.Service if repo.tags.nonEmpty => repo // tags already defined in repository.yaml
      case RepoType.Service                       =>
        val newTags =
          Option.when(repo.name.contains("stub"))(Tag.Stub)                         ++
          Option.when(isAdmin)(Tag.AdminFrontend)                                   ++
          Option.when(repo.name.contains("admin-frontend"))(Tag.AdminFrontend)      ++
          Option.when(ghRepo.exists(_.repoTypeHeuristics.hasPomXml))(Tag.Maven)     ++
          Option.when(servicesBuiltOffPlatform.contains(repo.name))(Tag.BuiltOffPlatform)
        repo.copy(tags = Some(newTags.toSet))
      case _                                      => repo
