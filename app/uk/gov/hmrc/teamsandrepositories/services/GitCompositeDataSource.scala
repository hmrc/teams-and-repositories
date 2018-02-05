package uk.gov.hmrc.teamsandrepositories.services

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import java.time.Instant
import play.api.Logger
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
class GitCompositeDataSource @Inject()(
  val githubConfig: GithubConfig,
  val persister: TeamsAndReposPersister,
  val mongoConnector: MongoConnector,
  val githubApiClientDecorator: GithubApiClientDecorator,
  val timestamper: Timestamper,
  val metrics: Metrics) {

  private val defaultMetricsRegistry = metrics.defaultRegistry

  val gitApiEnterpriseClient: GithubApiClient =
    githubApiClientDecorator
      .githubApiClient(githubConfig.githubApiEnterpriseConfig.apiUrl, githubConfig.githubApiEnterpriseConfig.key)

  val enterpriseTeamsRepositoryDataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig,
      gitApiEnterpriseClient,
      isInternal = true,
      timestamper.timestampF,
      metrics.defaultRegistry)

  val gitOpenClient: GithubApiClient =
    githubApiClientDecorator
      .githubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)

  val openTeamsRepositoryDataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig,
      gitOpenClient,
      isInternal = false,
      timestamper.timestampF,
      metrics.defaultRegistry)

  val dataSources = List(enterpriseTeamsRepositoryDataSource, openTeamsRepositoryDataSource)

  def persistTeamRepoMapping(implicit ec: ExecutionContext): Future[Seq[TeamRepositories]] = {
    val persistedTeams: Future[Seq[TeamRepositories]] = persister.getAllTeamAndRepos

    val sortedByUpdateDate = groupAndOrderTeamsAndTheirDataSources(persistedTeams)
    sortedByUpdateDate
      .flatMap { ts: Seq[OneTeamAndItsDataSources] =>
        Logger.debug("------ TEAM NAMES ------")
        ts.map(_.teamName).foreach(t => Logger.debug(t))
        Logger.debug("^^^^^^ TEAM NAMES ^^^^^^")

        val reposWithTeamsF = Future.sequence(ts.map { aTeam: OneTeamAndItsDataSources =>
          getAllRepositoriesForTeam(aTeam, persistedTeams)
            .map(mergeRepositoriesForTeam(aTeam.teamName, _))
            .flatMap(persister.update)
        })

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
    Future
      .sequence(dataSources.map(_.getAllRepositories))
      .map(_.flatten)
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
      teamsAndTheirOrgAndDataSources <- Future.sequence(dataSources.map(ds => ds.getTeamsWithOrgAndDataSourceDetails))
      persistedTeams                 <- persistedTeamsF
    } yield {
      val teamNameToSources: Map[String, List[TeamAndOrgAndDataSource]] =
        teamsAndTheirOrgAndDataSources.flatten.groupBy(_.team.name)
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
