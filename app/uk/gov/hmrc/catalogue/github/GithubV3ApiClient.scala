/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.catalogue.github

import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

import com.ning.http.client.AsyncHttpClientConfig.Builder
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsValue, Reads}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.libs.ws.{DefaultWSClientConfig, WSAuthScheme, WSRequestHolder, WSResponse}
import uk.gov.hmrc.catalogue.config.GithubCredentialsProvider

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

class Logger {
  def info(st: String) = println("[INFO] " + st)
  def debug(st: String) = Unit
}

trait GithubV3ApiClient {
  self : GithubEndpoints with GithubCredentialsProvider  =>

  val log = new Logger()

  private lazy val host = cred.host

  private val asyncBuilder: Builder = new Builder()
  private val tp: ExecutorService = Executors.newCachedThreadPool()
  asyncBuilder.setExecutorService(tp)

  private val builder: NingAsyncHttpClientConfigBuilder =
    new NingAsyncHttpClientConfigBuilder(
      config = new DefaultWSClientConfig(/*connectionTimeout = Some(120 * 1000)*/),
      builder = asyncBuilder)

  private val ws = new NingWSClient(builder.build())

  def getOrganisations = get[List[GhOrganization]](s"$rootUrl$organisationsEndpoint")

  def getTeamsForOrganisation(x: GhOrganization) = get[List[GhTeam]](s"$rootUrl${teamsForOrganisationEndpoint(x.login)}")

  def getReposForTeam(t: GhTeam) = get[List[GhRepository]](s"$rootUrl${reposForTeamEndpoint(t.id)}")

  private def buildCall(method: String, url: String, body: Option[JsValue] = None): WSRequestHolder = {
    log.debug(s"github client_id ${cred.user.takeRight(5)}")
    log.debug(s"github client_secret ${cred.key.takeRight(5)}")

    val req = ws.url(url)
      .withMethod(method)
      .withAuth(cred.user, cred.key, WSAuthScheme.BASIC)
      .withQueryString("client_id" -> cred.user, "client_secret" -> cred.key)
      .withHeaders("content-type" -> "application/json")

    body.map { b =>
      req.withBody(b)
    }.getOrElse(req)
  }

  private def callAndWait(req: WSRequestHolder): WSResponse = {
    log.info(s"${req.method} with ${req.url}")
    val result: WSResponse = Await.result(req.execute(), Duration.apply(1, TimeUnit.MINUTES))
    log.info(s"result ${result.status} - ${result.statusText} - ${result.body}")
    result
  }

  private def get[A](url: String)(implicit r: Reads[A]): Future[A] = {
    buildCall("GET", url).execute().flatMap { result =>
      result.status match {
        case s if s >= 200 && s < 300 => {
          Try {
            result.json.as[A]
          } match {
            case Success(a) => Future.successful(a)
            case Failure(e) => println(e.getMessage + "failed body was: " + result.body); Future.failed(e)
          }
        }
        case _@e =>
          Future.failed(new scala.Exception(s"Didn't get expected status code when writing to Github. Got status ${result.status}: url: ${url} ${result.body}"))
      }
    }
  }
}

