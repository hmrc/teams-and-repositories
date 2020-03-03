/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.githubclient.GitApiConfig
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.{ExecutionContext, Future}

class GithubConnector @Inject()(githubConfig: GithubConfig, http: HttpClient)(implicit ec: ExecutionContext) {

  def getFileContent(repoName: String, path: String): Future[Option[String]] = {
    implicit val hc = HeaderCarrier(authorization = Some(Authorization(s"token ${githubConfig.githubApiOpenConfig.key}")))
    http.GET[Option[HttpResponse]](url = s"${githubConfig.rawUrl}/hmrc/$repoName/master/$path")
      .map(_.map(_.body))
  }

  def getRateLimitMetrics(token: String): Future[RateLimitMetrics] = {
    implicit val hc = HeaderCarrier(authorization = Some(Authorization(s"token $token")))
    implicit val rlmr = RateLimitMetrics.reads
    http.GET[RateLimitMetrics](url = s"${githubConfig.url}/rate_limit")
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
