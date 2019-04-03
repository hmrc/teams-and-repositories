package uk.gov.hmrc.teamsandrepositories.controller.model

import java.net.URI

import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

import scala.util.Try

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
  githubUrl: Link,
  ci: Seq[Link]                  = Seq.empty,
  environments: Seq[Environment] = Seq.empty,
  language: String)

object RepositoryDetails {
  def create(repo: GitRepository, teamNames: Seq[String], urlTemplates: UrlTemplates): RepositoryDetails = {
    val repoDetails =
      RepositoryDetails(
        name        = repo.name,
        description = repo.description,
        isPrivate   = repo.isPrivate,
        createdAt   = repo.createdDate,
        lastActive  = repo.lastActiveDate,
        repoType    = repo.repoType,
        owningTeams = repo.owningTeams,
        teamNames   = teamNames,
        githubUrl   = Link("github-com", "GitHub.com", repo.url),
        language    = repo.language.getOrElse("")
      )

    repo.repoType match {
      case RepoType.Service =>
        repoDetails.copy(ci = buildCiUrls(repoDetails), environments = buildEnvironmentUrls(repo, urlTemplates))
      case RepoType.Library => repoDetails.copy(ci = buildCiUrls(repoDetails))
      case _                => repoDetails
    }
  }

  private def buildEnvironmentUrls(repository: GitRepository, urlTemplates: UrlTemplates): Seq[Environment] =
    urlTemplates.environments.map {
      case (name, tps) =>
        val links = tps.map { tp =>
          Link(tp.name, tp.displayName, tp.url(repository.name))
        }
        Environment(name, links)
    }.toSeq

  private def buildCiUrls(repo: RepositoryDetails): Seq[Link] = repo.teamNames match {
    case Seq(teamName) => buildCiUrl("Build", "Build", teamName, repo.name).toSeq
    case teamNames =>
      teamNames.flatMap(teamName => {
        val name = s"$teamName Build"
        buildCiUrl(name, name, teamName, repo.name)
      })
  }

  private def buildCiUrl(
    linkName: String,
    linkDisplayName: String,
    jobTeamName: String,
    repoName: String): Option[Link] =
    Try(
      new URI("https", "build.tax.service.gov.uk", s"/job/$jobTeamName/job/$repoName", null).toASCIIString
    ).toOption.map(url => Link(linkName, linkDisplayName, url))

}
