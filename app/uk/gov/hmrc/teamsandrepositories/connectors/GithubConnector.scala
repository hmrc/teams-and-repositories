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

package uk.gov.hmrc.teamsandrepositories.connectors

import java.time.Instant

import javax.inject.{Inject, Singleton}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, OFormat, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.{ExecutionContext, Future}

case class GhTeam(
  name: String,
  id  : Long
)
object GhTeam {
  val format: OFormat[GhTeam] =
    ( (__ \ "name").format[String]
    ~ (__ \ "id"  ).format[Long]
    )(apply, unlift(unapply))
}

case class GhRepository(
  id            : Long,
  name          : String,
  description   : Option[String],
  htmlUrl       : String,
  fork          : Boolean,
  createdDate   : Long, // TODO change to Instant
  lastActiveDate: Long, // TODO change to Instant
  isPrivate     : Boolean,
  language      : Option[String],
  archived      : Boolean, // TODO rename isArchived
  defaultBranch : String
)

object GhRepository {
  val format: OFormat[GhRepository] =
    ( (__ \ "id"            ).format[Long]
    ~ (__ \ "name"          ).format[String]
    ~ (__ \ "description"   ).formatNullable[String]
    ~ (__ \ "html_url"      ).format[String]
    ~ (__ \ "fork"          ).format[Boolean]
    ~ (__ \ "created_at"    ).format[Instant].inmap[Long](_.toEpochMilli, Instant.ofEpochMilli)
    ~ (__ \ "pushed_at"      ).format[Instant].inmap[Long](_.toEpochMilli, Instant.ofEpochMilli)
    ~ (__ \ "private"       ).format[Boolean]
    ~ (__ \ "language"      ).formatNullable[String]
    ~ (__ \ "archived"      ).format[Boolean]
    ~ (__ \ "default_branch").format[String]
    )(apply, unlift(unapply))
}

case class GhTag(name: String)
object GhTag {
  val format: OFormat[GhTag] =
    (__ \ "name").format[String].inmap(apply, unlift(unapply))
}

@Singleton
class GithubConnector @Inject()(
  githubConfig   : GithubConfig,
  httpClient     : HttpClient
)(implicit ec: ExecutionContext) {

  private val org = "hmrc"
  private val authHeader = "Authorization" -> s"token ${githubConfig.githubApiOpenConfig.key}"
  private val acceptsHeader = "Accepts" -> "application/vnd.github.v3+json"

  private implicit val hc = HeaderCarrier()

  def getFileContent(repoName: String, path: String): Future[Option[String]] =
    httpClient.GET[Option[HttpResponse]](
      url     = url"${githubConfig.rawUrl}/hmrc/$repoName/master/$path", // TODO check path is not escaped...
      headers = Seq(authHeader)
    ).map(_.map(_.body))

  def getTeams(): Future[List[GhTeam]] = {
    implicit val tf = GhTeam.format
    httpClient.GET[List[GhTeam]](
      url     = url"${githubConfig.githubApiOpenConfig.apiUrl}/orgs/$org/teams",
      headers = Seq(authHeader, acceptsHeader)
    )
  }

  def getReposForTeam(team: GhTeam): Future[List[GhRepository]] = {
    implicit val rf = GhRepository.format
    httpClient.GET[List[GhRepository]](
      url     = url"${githubConfig.githubApiOpenConfig.apiUrl}/orgs/$org/teams/${team.name}/repos",
      headers = Seq(authHeader, acceptsHeader)
    )
  }

  def getRepos(): Future[List[GhRepository]] = {
    implicit val rf = GhRepository.format
    httpClient.GET[List[GhRepository]](
      url     = url"${githubConfig.githubApiOpenConfig.apiUrl}/orgs/$org/repos",
      headers = Seq(authHeader, acceptsHeader)
    )
  }

  def getTags(repo: GhRepository): Future[List[GhTag]] = {
    implicit val tf = GhTag.format
    httpClient.GET[List[GhTag]](
      url     = url"${githubConfig.githubApiOpenConfig.apiUrl}/repos/$org/${repo.name}/tags",
      headers = Seq(authHeader, acceptsHeader)
    )
  }

  def repoContainsContent(repo: GhRepository, path: String): Future[Boolean] =
    httpClient.GET[Option[HttpResponse]](
      url     = url"${githubConfig.githubApiOpenConfig.apiUrl}/repos/$org/${repo.name}/contents/$path", // TODO add path without escaping...
      headers = Seq(authHeader, acceptsHeader)
    ).map(_.fold(false)(_.body.isEmpty))

  def getRateLimitMetrics(token: String): Future[RateLimitMetrics] = {
    implicit val rlmr = RateLimitMetrics.reads
    httpClient.GET[RateLimitMetrics](
      url     = s"${githubConfig.url}/rate_limit",
      headers = Seq("Authorization" -> s"token $token")
    )
  }
}

case class RateLimitMetrics(limit: Int, remaining: Int, reset: Int)

object RateLimitMetrics {
  val reads: Reads[RateLimitMetrics] =
    Reads.at(__ \ "rate")(
      ( (__ \ "limit"    ).read[Int]
      ~ (__ \ "remaining").read[Int]
      ~ (__ \ "reset"    ).read[Int]
      )(RateLimitMetrics.apply _)
    )
}
