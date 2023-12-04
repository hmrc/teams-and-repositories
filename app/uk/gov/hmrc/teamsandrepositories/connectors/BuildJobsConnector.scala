/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BuildJobsConnector @Inject()(
  githubConfig: GithubConfig,
  httpClientV2: HttpClientV2
)(implicit ec: ExecutionContext) {
  private val authHeader = "Authorization" -> s"token ${githubConfig.key}"

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  def getBuildjobFiles(): Future[Seq[BuildJobFilename]] = {
    implicit val bjfr = Reads.at[String](__ \ "name").map(BuildJobFilename.apply)
    httpClientV2.get(url"${githubConfig.apiUrl}/repos/hmrc/build-jobs/contents/jobs/live")
      .setHeader(authHeader)
      .withProxy
      .execute[Seq[BuildJobFilename]]
  }

  def getBuildjobFileContent(name: BuildJobFilename): Future[String] = {
    implicit val rds: HttpReads[String] = HttpReads[HttpResponse].map(_.body)
    httpClientV2
      .get(url"${githubConfig.apiUrl}/repos/hmrc/build-jobs/contents/jobs/live/${name.name}")
      .setHeader(authHeader)
      .setHeader("Accept" -> "application/vnd.github.VERSION.raw")
      .withProxy
      .execute[String]
  }
}

case class BuildJobFilename(name: String)
