package uk.gov.hmrc.teamsandrepositories.services

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import java.time.Instant

import play.api.{Configuration, Logger}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.teamsandrepositories.controller.BlockingIOExecutionContext
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}

@Singleton
class Timestamper {
  def timestampF() = Instant.now().toEpochMilli
}

@Singleton
case class GitCompositeDataSource @Inject()(
  githubConfig: GithubConfig,
  persister: TeamsAndReposPersister,
  mongoConnector: MongoConnector,
  githubApiClientDecorator: GithubApiClientDecorator,
  timestamper: Timestamper,
  metrics: Metrics,
  configuration: Configuration) {

  import scala.collection.JavaConverters._

  private val defaultMetricsRegistry = metrics.defaultRegistry

  val repositoriesToIgnore: List[String] =
    configuration.getStringList("shared.repositories").fold(List.empty[String])(_.asScala.toList)

  val gitOpenClient: GithubApiClient =
    githubApiClientDecorator
      .githubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)

  val dataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig,
      gitOpenClient,
      timestamper.timestampF,
      defaultMetricsRegistry,
      repositoriesToIgnore
    )

  def serialiseFutures[A, B](l: Iterable[A])(fn: A => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] =
    l.foldLeft(Future.successful(List.empty[B])) { (previousFuture, next) ⇒
      for {
        previousResults ← previousFuture
        next ← fn(next)
      } yield previousResults :+ next
    }

  def persistTeamRepoMapping(implicit ec: ExecutionContext): Future[Seq[TeamRepositories]] = {
    val persistedTeams: Future[Seq[TeamRepositories]] = persister.getAllTeamAndRepos

    val sortedByUpdateDate = groupAndOrderTeamsAndTheirDataSources(persistedTeams)
    sortedByUpdateDate
      .flatMap { ts: Seq[OneTeamAndItsDataSources] =>
        Logger.debug("------ TEAM NAMES ------")
        ts.map(_.teamName).foreach(t => Logger.debug(t))
        Logger.debug("^^^^^^ TEAM NAMES ^^^^^^")

        val reposWithTeamsF: Future[Seq[TeamRepositories]] = serialiseFutures(ts) { aTeam: OneTeamAndItsDataSources =>
          getAllRepositoriesForTeam(aTeam, persistedTeams)
            .map(mergeRepositoriesForTeam(aTeam.teamName, _))
            .flatMap(persister.update)
        }

        for {
          withTeams    <- reposWithTeamsF
          withoutTeams <- getRepositoriesWithoutTeams(withTeams).flatMap(persister.update)
        } yield {
          withTeams :+ withoutTeams
        }

      }
      .map(_.toSeq) recover {
      case e =>
        Logger.error("Could not persist to teams repo.", e)
        throw e
    }
  }

  def getRepositoriesWithoutTeams(persistedReposWithTeams: Seq[TeamRepositories])(
    implicit ec: ExecutionContext): Future[TeamRepositories] =
    dataSource.getAllRepositories
      .map { repos =>
        val reposWithoutTeams = {
          val urlsOfPersistedRepos = persistedReposWithTeams.flatMap(_.repositories.map(_.url)).toSet
          repos.filterNot(r => urlsOfPersistedRepos.contains(r.url))
        }
        TeamRepositories(
          teamName     = TeamRepositories.TEAM_UNKNOWN,
          repositories = reposWithoutTeams,
          updateDate   = timestamper.timestampF()
        )
      }

  private def getAllRepositoriesForTeam(aTeam: OneTeamAndItsDataSources, persistedTeams: Future[Seq[TeamRepositories]])(
    implicit ec: ExecutionContext): Future[Seq[TeamRepositories]] =
    Future.sequence(aTeam.teamAndDataSources.map { teamAndDataSource =>
      teamAndDataSource.dataSource
        .mapTeam(teamAndDataSource.organisation, teamAndDataSource.team, persistedTeams)
    })

  private def groupAndOrderTeamsAndTheirDataSources(persistedTeamsF: Future[Seq[TeamRepositories]])(
    implicit ec: ExecutionContext): Future[Seq[OneTeamAndItsDataSources]] =
    (for {
      teamsAndTheirOrgAndDataSources <- dataSource.getTeamsWithOrgAndDataSourceDetails
      persistedTeams                 <- persistedTeamsF
    } yield {
      val teamNameToSources: Map[String, List[TeamAndOrgAndDataSource]] =
        teamsAndTheirOrgAndDataSources.groupBy(_.team.name)
      teamNameToSources.map {
        case (teamName, tds) =>
          OneTeamAndItsDataSources(teamName, tds, persistedTeams.find(_.teamName == teamName).fold(0L)(_.updateDate))
      }
    }.toSeq.sortBy(_.updateDate)).recover {
      case ex =>
        Logger.error(ex.getMessage, ex)
        throw ex
    }

  private def mergeRepositoriesForTeam(
    teamName: String,
    aTeamAndItsRepositories: Seq[TeamRepositories]): TeamRepositories = {
    val teamRepositories = aTeamAndItsRepositories.foldLeft(TeamRepositories(teamName, Nil, timestamper.timestampF())) {
      case (acc, tr) =>
        acc.copy(repositories = acc.repositories ++ tr.repositories)
    }
    teamRepositories.copy(repositories = teamRepositories.repositories.sortBy(_.name))
  }

  def removeOrphanTeamsFromMongo(teamRepositoriesFromGh: Seq[TeamRepositories])(implicit ec: ExecutionContext) = {

    import BlockingIOExecutionContext._

    val teamNamesFromMongo: Future[Set[String]] = {
      persister.getAllTeamAndRepos.map {
        case (allPersistedTeamAndRepositories) =>
          allPersistedTeamAndRepositories.map(_.teamName).toSet
      }
    }

    val teamNamesFromGh = teamRepositoriesFromGh.map(_.teamName)

    val orphanTeams: Future[Set[String]] = for {
      mongoTeams <- teamNamesFromMongo
    } yield mongoTeams.filterNot(teamNamesFromGh.toSet)

    orphanTeams.flatMap { (teamNames: Set[String]) =>
      Logger.info(s"Removing these orphan teams:[$teamNames]")
      persister.deleteTeams(teamNames)
    }
  } recover {
    case e =>
      Logger.error("Could not remove orphan teams from mongo.", e)
      throw e
  }

}

@Singleton
case class GithubApiClientDecorator @Inject()() {
  def githubApiClient(apiUrl: String, apiToken: String) = GithubApiClient(apiUrl, apiToken)
}
