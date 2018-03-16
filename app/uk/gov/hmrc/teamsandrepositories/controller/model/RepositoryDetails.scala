package uk.gov.hmrc.teamsandrepositories.controller.model

import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

case class Environment(name: String, services: Seq[Link])

case class Link(name: String, displayName: String, url: String)

case class RepositoryDetails(
  name: String,
  description: String,
  isPrivate: Boolean,
  createdAt: Long,
  lastActive: Long,
  repoType: RepoType.RepoType,
  owningTeams: Seq[String],
  teamNames: Seq[String],
  githubUrls: Seq[Link],
  ci: Seq[Link]                  = Seq.empty,
  environments: Seq[Environment] = Seq.empty,
  language: String)

object RepositoryDetails {
  def buildRepositoryDetails(
    repo: GitRepository,
    teamNames: Seq[String],
    urlTemplates: UrlTemplates): RepositoryDetails = {

    val repoDetails = RepositoryDetails(
      repo.name,
      repo.description,
      repo.isPrivate,
      repo.createdDate,
      repo.lastActiveDate,
      repo.repoType,
      repo.owningTeams,
      teamNames,
      Seq(Link("github-com", "GitHub.com", repo.url)),
      language = repo.language.getOrElse("")
    )

    if (hasEnvironment(repo)) {
      repoDetails.copy(ci = buildCiUrls(repo, urlTemplates), environments = buildEnvironmentUrls(repo, urlTemplates))
    } else if (hasBuild(repo)) {
      repoDetails.copy(ci = buildCiUrls(repo, urlTemplates))
    } else {
      repoDetails
    }
  }

  def determineLanguage(allRepositories: Seq[GitRepository]): String = {
    val language: String = allRepositories.reverse
      .foldLeft("")((b, repo) => {
        val lang = repo.language.getOrElse("")
        if (lang == "") b
        else lang
      })
    language
  }

  private def hasEnvironment(repo: GitRepository): Boolean = repo.repoType == RepoType.Service

  private def hasBuild(repo: GitRepository): Boolean = repo.repoType == RepoType.Library

  private def buildEnvironmentUrls(repository: GitRepository, urlTemplates: UrlTemplates): Seq[Environment] =
    urlTemplates.environments.map {
      case (name, tps) =>
        val links = tps.map { tp =>
          Link(tp.name, tp.displayName, tp.url(repository.name))
        }
        Environment(name, links)
    }.toSeq

  private def buildCiUrls(repository: GitRepository, urlTemplates: UrlTemplates): List[Link] =
    if (repository.isPrivate) {
      buildUrls(repository, urlTemplates.ciClosed)
    } else {
      buildUrls(repository, urlTemplates.ciOpen)
    }

  private def buildUrls(repo: GitRepository, templates: Seq[UrlTemplate]) =
    templates.map(t => Link(t.name, t.displayName, t.url(repo.name))).toList

}
