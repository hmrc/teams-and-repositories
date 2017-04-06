package uk.gov.hmrc.teamsandrepositories

import java.net.URLDecoder
import java.time.{LocalDateTime, ZoneOffset}

import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.TeamRepositoryWrapper.{RepositoriesToTeam, RepositoryToTeam}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates


case class TeamRepositories(teamName: String,
                            repositories: List[GitRepository]) {
  def repositoriesByType(repoType: RepoType.RepoType) = repositories.filter(_.repoType == repoType)
}

object TeamRepositories {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats = Json.format[TeamRepositories]

  def getTeamList(teamRepos: Seq[TeamRepositories], repositoriesToIgnore: List[String]): Seq[Team] =
    teamRepos.map(_.teamName).map { tn =>
      val repos: Seq[GitRepository] = teamRepos.filter(_.teamName == tn).flatMap(_.repositories)
      val team = Team(name = tn, repos = None)
      if (repos.nonEmpty) {
        val teamActivityDates = GitRepository.getTeamActivityDatesOfNonSharedRepos(repos, repositoriesToIgnore)
        team.copy(firstActiveDate = teamActivityDates.firstActiveDate, lastActiveDate = teamActivityDates.lastActiveDate)
      } else team

    }

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] =
    teamRepos
      .flatMap(_.repositories)
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

