package uk.gov.hmrc.teamsandrepositories

import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import play.api.libs.functional.syntax._


case class GitRepository(name: String,
                         description: String,
                         url: String,
                         createdDate: Long,
                         lastActiveDate: Long,
                         isInternal: Boolean = false,
                         isPrivate: Boolean = false,
                         repoType: RepoType = RepoType.Other,
                         digitalServiceName: Option[String] = None)

object GitRepository {
  def toRepository(gitRepository: GitRepository): Repository =
    Repository(gitRepository.name, gitRepository.createdDate, gitRepository.lastActiveDate, gitRepository.repoType)

  implicit val gitRepositoryFormats: OFormat[GitRepository] = {

    val reads: Reads[GitRepository] = (
      (JsPath \ "name").read[String] and
        (JsPath \ "description").read[String] and
        (JsPath \ "url").read[String] and
        (JsPath \ "createdDate").read[Long] and
        (JsPath \ "lastActiveDate").read[Long] and
        (JsPath \ "isInternal").read[Boolean] and
        (JsPath \ "isPrivate").readNullable[Boolean].map(_.getOrElse(false)) and
        (JsPath \ "repoType").read[RepoType] and
        (JsPath \ "digitalServiceName").readNullable[String]
      ) (GitRepository.apply _)

    val writes = Json.writes[GitRepository]

    OFormat(reads, writes)
  }

  case class TeamActivityDates(firstActiveDate: Option[Long] = None,
                               lastActiveDate: Option[Long] = None,
                               firstServiceCreationDate: Option[Long] = None)

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

