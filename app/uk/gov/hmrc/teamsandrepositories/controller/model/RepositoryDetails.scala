/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.controller.model

import java.net.URI

import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}

import scala.util.{Failure, Success, Try}

case class Environment(name: String, services: Seq[Link])

case class Link(name: String, displayName: String, url: String)

case class RepositoryDetails(
  name        : String,
  description : String,
  isPrivate   : Boolean,
  createdAt   : Long,
  lastActive  : Long,
  repoType    : RepoType.RepoType,
  owningTeams : Seq[String],
  teamNames   : Seq[String],
  githubUrl   : Link,
  ci          : Seq[Link]        = Seq.empty,
  environments: Seq[Environment] = Seq.empty,
  language    : String,
  isArchived  : Boolean
)

object RepositoryDetails {

  private val logger = Logger(this.getClass)

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
        language    = repo.language.getOrElse(""),
        isArchived  = repo.archived
      )

    repo.repoType match {
      case RepoType.Service => repoDetails.copy(
                                 ci           = buildCiUrls(repoDetails),
                                 environments = buildEnvironmentUrls(repo, urlTemplates)
                               )
      case RepoType.Library => repoDetails.copy(ci = buildCiUrls(repoDetails))
      case _                => repoDetails
    }
  }

  private def buildEnvironmentUrls(repository: GitRepository, urlTemplates: UrlTemplates): Seq[Environment] =
    urlTemplates.environments.map {
      case (name, tps) =>
        Environment(
          name,
          services = tps.map(tp => Link(tp.name, tp.displayName, tp.url(repository.name)))
        )
    }.toSeq

  private def buildCiUrls(repo: RepositoryDetails): Seq[Link] =
    repo.teamNames match {
      case Seq(teamName) => buildCiUrl("Build", "Build", teamName, repo.name).toSeq
      case teamNames =>
        teamNames.flatMap { teamName =>
          val name = s"$teamName Build"
          buildCiUrl(name, name, teamName, repo.name)
        }
    }

  private def buildCiUrl(
    linkName       : String,
    linkDisplayName: String,
    jobTeamName    : String,
    repoName       : String
  ): Option[Link] =
    Try {
      new URI("https", "build.tax.service.gov.uk", s"/job/$jobTeamName/job/$repoName", null).toASCIIString
    } match {
      case Success(url) => Some(Link(linkName, linkDisplayName, url))
      case Failure(throwable) =>
        logger.warn(s"Unable to create build ci url for teamName: $jobTeamName and repoName: $repoName", throwable)
        None
    }
}
