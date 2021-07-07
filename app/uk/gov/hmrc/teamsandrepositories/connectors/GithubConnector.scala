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

import javax.inject.{Inject, Singleton}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, OFormat, __}
import uk.gov.hmrc.githubclient.{GhRepository, GithubApiClient}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.{ExecutionContext, Future}

case class GhTeam(name: String, id: Long)
object GhTeam {
  val format: OFormat[GhTeam] =
    ( (__ \ "name").format[String]
    ~ (__ \ "id"  ).format[Long]
    )(apply, unlift(unapply))
}

@Singleton
class GithubConnector @Inject()(
  githubConfig   : GithubConfig,
  httpClient     : HttpClient
)(implicit ec: ExecutionContext) {

  private val githubApiClient: GithubApiClient =
    GithubApiClient(githubConfig.githubApiOpenConfig.apiUrl, githubConfig.githubApiOpenConfig.key)

  private implicit val hc = HeaderCarrier()

  def getFileContent(repoName: String, path: String): Future[Option[String]] =
    httpClient.GET[Option[HttpResponse]](
      url     = url"${githubConfig.rawUrl}/hmrc/$repoName/master/$path",
      headers = Seq("Authorization" -> s"token ${githubConfig.githubApiOpenConfig.key}")
    ).map(_.map(_.body))

  def getTeamsForOrg(org: String): Future[List[GhTeam]] = {
    implicit val tf = GhTeam.format
    httpClient.GET[List[GhTeam]](
      url     = url"${githubConfig.githubApiOpenConfig.apiUrl}/orgs/$org/teams",
      headers = Seq("Authorization" -> s"token ${githubConfig.githubApiOpenConfig.key}")
    )
  }

  def getReposForTeam(team: GhTeam): Future[List[GhRepository]] =
    githubApiClient.getReposForTeam(team.id)

  def getReposForOrg(org: String): Future[List[GhRepository]] =
    githubApiClient.getReposForOrg(org)

  def getTags(org: String, repository: GhRepository): Future[List[String]] =
    githubApiClient.getTags(org, repository.name)

  def repoContainsContent(path: String, repo: GhRepository, org: String): Future[Boolean] =
    githubApiClient.repoContainsContent(path, repo.name, org)

  def getRateLimitMetrics(token: String): Future[RateLimitMetrics] = {
    implicit val hc = HeaderCarrier()
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
