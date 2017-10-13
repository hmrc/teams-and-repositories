package uk.gov.hmrc.teamsandrepositories.controller.model

import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

case class Environment(name: String, services: Seq[Link])

case class Link(name: String, displayName: String, url: String)

case class RepositoryDetails(name: String,
                             description: String,
                             isPrivate: Boolean,
                             createdAt: Long,
                             lastActive: Long,
                             repoType: RepoType.RepoType,
                             teamNames: Seq[String],
                             githubUrls: Seq[Link],
                             ci: Seq[Link] = Seq.empty,
                             environments: Seq[Environment] = Seq.empty,
                             language: String)

object RepositoryDetails {
  def buildRepositoryDetails(primaryRepository: Option[GitRepository],
                                     allRepositories: Seq[GitRepository],
                                     teamNames: Seq[String],
                                     urlTemplates: UrlTemplates): Option[RepositoryDetails] = {

    primaryRepository.map { repo =>

      val sameNameRepos: Seq[GitRepository] = allRepositories.filter(r => r.name == repo.name)
      val createdDate = sameNameRepos.minBy(_.createdDate).createdDate
      val lastActiveDate = sameNameRepos.maxBy(_.lastActiveDate).lastActiveDate

      val language: String = determineLanguage(allRepositories)

      val repoDetails = RepositoryDetails(
        repo.name,
        repo.description,
        repo.isPrivate,
        createdDate,
        lastActiveDate,
        repo.repoType,
        teamNames,
        allRepositories.map { repo =>
          Link(
            githubName(repo.isInternal),
            githubDisplayName(repo.isInternal),
            repo.url)
        },
        language = language)

      val repositoryForCiUrls: GitRepository = allRepositories.find(!_.isInternal).fold(repo)(identity)

      if (hasEnvironment(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates), environments = buildEnvironmentUrls(repo, urlTemplates))
      else if (hasBuild(repo))
        repoDetails.copy(ci = buildCiUrls(repositoryForCiUrls, urlTemplates))
      else repoDetails
    }
  }

  def determineLanguage(allRepositories: Seq[GitRepository]): String = {
    val language: String = allRepositories.sortBy(x => x.isInternal).reverse.foldLeft("")((b, repo) => {
      val lang = repo.language.getOrElse("")
      if (lang == "") b
      else lang
    })
    language
  }

  private def githubName(isInternal: Boolean) = if (isInternal) "github-enterprise" else "github-com"

  private def githubDisplayName(isInternal: Boolean) = if (isInternal) "Github Enterprise" else "GitHub.com"

  private def hasEnvironment(repo: GitRepository): Boolean = repo.repoType == RepoType.Service

  private def hasBuild(repo: GitRepository): Boolean = repo.repoType == RepoType.Library

  private def buildEnvironmentUrls(repository: GitRepository, urlTemplates: UrlTemplates): Seq[Environment] = {
    urlTemplates.environments.map { case (name, tps) =>
      val links = tps.map { tp => Link(tp.name, tp.displayName, tp.url(repository.name)) }
      Environment(name, links)
    }.toSeq
  }

  private def buildCiUrls(repository: GitRepository, urlTemplates: UrlTemplates): List[Link] =
    repository.isInternal || repository.isPrivate match {
      case true => buildUrls(repository, urlTemplates.ciClosed)
      case false => buildUrls(repository, urlTemplates.ciOpen)
    }

  private def buildUrls(repo: GitRepository, templates: Seq[UrlTemplate]) =
    templates.map(t => Link(t.name, t.displayName, t.url(repo.name))).toList

}