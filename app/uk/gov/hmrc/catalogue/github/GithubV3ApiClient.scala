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

import java.util.concurrent.{ExecutorService, Executors}

import com.ning.http.client.AsyncHttpClientConfig.Builder
import play.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsValue, Reads}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.libs.ws.{WSResponse, DefaultWSClientConfig, WSAuthScheme, WSRequestHolder}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


trait GithubV3ApiClient {
  self: GithubEndpoints with GithubCredentialsProvider =>

  val log = new Logger()

  private val asyncBuilder: Builder = new Builder()
  private val tp: ExecutorService = Executors.newCachedThreadPool()
  asyncBuilder.setExecutorService(tp)

  private val builder: NingAsyncHttpClientConfigBuilder =
    new NingAsyncHttpClientConfigBuilder(
      config = new DefaultWSClientConfig(/*connectionTimeout = Some(120 * 1000)*/),
      builder = asyncBuilder)

  private val ws = new NingWSClient(builder.build())

  def getOrganisations = {
    val url = s"$rootUrl$organisationsEndpoint"

    get[List[GhOrganisation]](url).map(result => {
      Logger.info(s"Got ${result.length} organisations from $url")
      result
    })
  }

  def getTeamsForOrganisation(o: GhOrganisation) = {
    val url = s"$rootUrl${teamsForOrganisationEndpoint(o.login)}"

    get[List[GhTeam]](url).map(result => {
      Logger.info(s"Got ${result.length} teams for ${o.login} from $url")
      result
    })
  }

  def getReposForTeam(t: GhTeam) = {
    val url = s"$rootUrl${reposForTeamEndpoint(t.id)}"

    get[List[GhRepository]](url).map(result => {
      Logger.info(s"Got ${result.length} repositories for ${t.name} from $url")
      result
    })
  }

  def containsAppFolder(o: GhOrganisation, r: GhRepository) = {
    val organisation = o.login
    val url = s"$rootUrl${repoContentsEndPoint(organisation, r.name)}/app"

    head(url).map(result => {
      Logger.info(s"Got $result when checking for app folder in $organisation/${r.name} from $url")
      (r, result == 200)
    })
  }

  private def buildCall(method: String, url: String, body: Option[JsValue] = None): WSRequestHolder = {
    val req = ws.url(url)
      .withMethod(method)
      .withAuth(cred.user, cred.key, WSAuthScheme.BASIC)
      .withQueryString("client_id" -> cred.user, "client_secret" -> cred.key)
      .withHeaders("content-type" -> "application/json")

    body.map { b =>
      req.withBody(b)
    }.getOrElse(req)
  }

  private def get[T](url: String)(implicit r: Reads[T]): Future[T] = withErrorHandling("GET", url) {
    case _@s if s.status >= 200 && s.status < 300 =>
      Try {
        s.json.as[T]
      } match {
        case Success(a) => a
        case Failure(e) =>
          Logger.error(s"Error paring response failed body was: ${s.body} root url : $rootUrl")
          throw e
      }
    case res =>
      throw new RuntimeException(s"Unexpected response status : ${res.status}  calling url : $url response body : ${res.body}")
  }


  private def withErrorHandling[T](method: String, url: String)(f: WSResponse => T): Future[T] = {
    buildCall(method, url).execute().transform(
      f,
      _ => throw new RuntimeException(s"Error connecting  $url")
    )
  }

  private def head(url: String): Future[Int] = withErrorHandling("HEAD", url)(_.status)

}

