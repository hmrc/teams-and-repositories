package uk.gov.hmrc.teamsandrepositories

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.TeamRepositoryWrapper.TeamActivityDates
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates

case class GitRepository(name: String,
                         description: String,
                         url: String,
                         createdDate: Long,
                         lastActiveDate: Long,
                         isInternal: Boolean = false,
                         repoType: RepoType = RepoType.Other)

object GitRepository {
  implicit val gitRepositoryFormats: OFormat[GitRepository] = Json.format[GitRepository]

  def getTeamActivityDatesOfNonSharedRepos(repos: Seq[GitRepository], repositoriesToIgnore: List[String]): TeamActivityDates = {

    val nonIgnoredRepos = repos.filterNot(r => repositoriesToIgnore.contains(r.name))

    if (nonIgnoredRepos.nonEmpty) {
      val firstServiceCreationDate =
        if (nonIgnoredRepos.exists(_.repoType == RepoType.Service))
          Some(getCreatedAtDate(nonIgnoredRepos.filter(_.repoType == RepoType.Service)))
        else
          None

      TeamActivityDates(Some(getCreatedAtDate(nonIgnoredRepos)), Some(getLastActiveDate(nonIgnoredRepos)), firstServiceCreationDate)
    }
    else {
      TeamActivityDates()
    }
  }

  def primaryRepoType(repositories: Seq[GitRepository]): RepoType = {
    if (repositories.exists(_.repoType == RepoType.Prototype)) RepoType.Prototype
    else if (repositories.exists(_.repoType == RepoType.Service)) RepoType.Service
    else if (repositories.exists(_.repoType == RepoType.Library)) RepoType.Library
    else RepoType.Other
  }

  def getCreatedAtDate(repos: Seq[GitRepository]): Long =
    repos.minBy(_.createdDate).createdDate

  def getLastActiveDate(repos: Seq[GitRepository]): Long =
    repos.maxBy(_.lastActiveDate).lastActiveDate

  def repoGroupToRepositoryDetails(repoType: RepoType,
                                   repositories: Seq[GitRepository],
                                   teamNames: Seq[String],
                                   urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    val primaryRepository = extractRepositoryGroupForType(repoType, repositories).find(_.repoType == repoType)

    RepositoryDetails.buildRepositoryDetails(primaryRepository, repositories, teamNames, urlTemplates)

  }

  def extractRepositoryGroupForType(repoType: RepoType.RepoType, repositories: Seq[GitRepository]): List[GitRepository] = {
    repositories
      .groupBy(_.name)
      .filter {
        case (name, repos) if repoType == RepoType.Service =>
          repos.exists(x => x.repoType == RepoType.Service)
        case (name, repos) if repoType == RepoType.Library =>
          !repos.exists(x => x.repoType == RepoType.Service) && repos.exists(x => x.repoType == RepoType.Library)
        case (name, repos) =>
          !repos.exists(x => x.repoType == RepoType.Service) && !repos.exists(x => x.repoType == RepoType.Library) && repos.exists(x => x.repoType == repoType)
      }
      .flatMap(_._2).toList
  }

}

