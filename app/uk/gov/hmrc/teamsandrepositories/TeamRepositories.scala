package uk.gov.hmrc.teamsandrepositories

import java.net.URLDecoder
import java.time.{LocalDateTime, ZoneOffset}

import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates


case class TeamRepositories(teamName: String,repositories: List[GitRepository])

object TeamRepositories {
  case class DigitalServiceRepository(name: String, createdAt: Long, lastUpdatedAt: Long, repoType: RepoType.RepoType, teamNames: Seq[String])

  object DigitalServiceRepository {
    implicit val digitalServiceFormat = Json.format[DigitalServiceRepository]
  }

  case class DigitalService(name: String, lastUpdatedAt: Long, repositories: Seq[DigitalServiceRepository])

  object DigitalService {
    implicit val digitalServiceFormat = Json.format[DigitalService]
  }

  def findDigitalServiceDetails(allTeamsAndRepos: Seq[TeamRepositories], digitalServiceName: String): Option[DigitalService] = {

    /**
      * I want to have a list of team names related to each repository
      */

//    val x: Map[String, Seq[(GitRepository, String)]] =
//      allTeamsAndRepos
//        .flatMap(teamAndRepos => teamAndRepos.repositories.map(_ -> teamAndRepos.teamName))
//        .groupBy(_._1.name)
//        .map {
//          case (repoName, gitReposAndTeamName) =>
//        }

    val gitRepositories =
      allTeamsAndRepos
        .flatMap(_.repositories)
        .filter(_.digitalServiceName.contains(digitalServiceName))

    identifyRepositories(gitRepositories) match {
        case Nil => None
        case repos => Some(DigitalService(
          digitalServiceName,
          repos.map(_.lastUpdatedAt).max,
          repos.map(repo => DigitalServiceRepository(repo.name, repo.createdAt, repo.lastUpdatedAt, repo.repoType, Seq("Whatever")))))
      }
  }

  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats: OFormat[TeamRepositories] =
    Json.format[TeamRepositories]

  case class RepositoryToTeam(repositoryName: String, teamName: String)

  case class RepositoriesToTeam(repositories: Seq[GitRepository], teamName: String)

  def getTeamList(teamRepos: Seq[TeamRepositories], repositoriesToIgnore: List[String]): Seq[Team] =
    teamRepos.map(_.teamName).map { tn =>
      val repos: Seq[GitRepository] = teamRepos.filter(_.teamName == tn).flatMap(_.repositories)
      val team = Team(name = tn, repos = None)
      if (repos.nonEmpty) {
        val teamActivityDates = GitRepository.getTeamActivityDatesOfNonSharedRepos(repos, repositoriesToIgnore)
        team.copy(firstActiveDate = teamActivityDates.firstActiveDate, lastActiveDate = teamActivityDates.lastActiveDate)
      } else team

    }

  def identifyRepositories(gitRepositories: Seq[GitRepository]): Seq[Repository] =
    gitRepositories
      .groupBy(_.name)
      .map {
        case (repositoryName, repositories) =>
          Repository(
            repositoryName,
            repositories.minBy(_.createdDate).createdDate,
            repositories.maxBy(_.lastActiveDate).lastActiveDate,
            GitRepository.primaryRepoType(repositories))
      }
      .toList
      .sortBy(_.name.toUpperCase)

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] =
    identifyRepositories(teamRepos.flatMap(_.repositories))

  def findRepositoryDetails(teamRepos: Seq[TeamRepositories], repoName: String, ciUrlTemplates: UrlTemplates): Option[RepositoryDetails] = {
    teamRepos.foldLeft((Set.empty[String], Set.empty[GitRepository])) { case ((ts, repos), tr) =>
      if (tr.repositories.exists(_.name == repoName))
        (ts + tr.teamName, repos ++ tr.repositories.filter(_.name == repoName))
      else (ts, repos)
    } match {
      case (teams, repos) if repos.nonEmpty =>
        GitRepository.repoGroupToRepositoryDetails(GitRepository.primaryRepoType(repos.toSeq), repos.toSeq, teams.toSeq.sorted, ciUrlTemplates)
      case _ => None
    }
  }

  def getTeamRepositoryNameList(teamRepos: Seq[TeamRepositories], teamName: String): Option[Map[RepoType.RepoType, List[String]]] = {
    val decodedTeamName = URLDecoder.decode(teamName, "UTF-8")
    teamRepos.find(_.teamName == decodedTeamName).map { t =>

      RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, rtype) =>
        m + (rtype -> GitRepository.extractRepositoryGroupForType(rtype, t.repositories).map(_.name).distinct.sortBy(_.toUpperCase))
      }

    }
  }

  def getRepositoryDetailsList(teamRepos: Seq[TeamRepositories], repoType: RepoType, ciUrlTemplates: UrlTemplates): Seq[RepositoryDetails] = {
    getRepositoryTeams(teamRepos)
      .groupBy(_.repositories)
      .flatMap { case (repositories, teamsAndRepos: Seq[RepositoriesToTeam]) => GitRepository.repoGroupToRepositoryDetails(repoType, repositories, teamsAndRepos.map(_.teamName), ciUrlTemplates) }
      .toSeq
      .sortBy(_.name.toUpperCase)
  }

  def getRepositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoriesToTeam] =
    for {
      teamAndRepositories <- data
      repositories <- teamAndRepositories.repositories.groupBy(_.name).values
    } yield RepositoriesToTeam(repositories, teamAndRepositories.teamName)

  def findTeam(teamRepos: Seq[TeamRepositories], teamName: String, repositoriesToIgnore: List[String]): Option[Team] = {

    teamRepos
      .find(_.teamName == URLDecoder.decode(teamName, "UTF-8"))
      .map { teamRepositories =>

        val teamActivityDates = GitRepository.getTeamActivityDatesOfNonSharedRepos(teamRepositories.repositories, repositoriesToIgnore)

        def getRepositoryDisplayDetails(repoType: RepoType.Value): List[String] = {
          teamRepositories.repositories
            .filter(_.repoType == repoType)
            .map(_.name)
            .distinct
            .sortBy(_.toUpperCase)
        }

        val repos = RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, repoType) =>
          m + (repoType -> getRepositoryDisplayDetails(repoType))
        }

        Team(teamName, teamActivityDates.firstActiveDate, teamActivityDates.lastActiveDate, teamActivityDates.firstServiceCreationDate, Some(repos))
      }
  }

  def getRepositoryToTeamNameList(teamRepos: Seq[TeamRepositories]): Map[String, Seq[String]] = {
    val mappings = for {
      tr <- teamRepos
      r <- tr.repositories
    } yield RepositoryToTeam(r.name, tr.teamName)

    mappings.groupBy(_.repositoryName)
      .map { m => m._1 -> m._2.map(_.teamName).distinct }
  }
}

