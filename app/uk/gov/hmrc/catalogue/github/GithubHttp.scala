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

import java.io.File
import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

import com.ning.http.client.AsyncHttpClientConfig.Builder
import org.joda.time.DateTime
import play.api.libs.json.{Json, JsValue, Reads}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}
import play.api.libs.ws.{DefaultWSClientConfig, WSAuthScheme, WSRequestHolder, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}


class Logger {
  def info(st: String) = println("[INFO] " + st)

  def debug(st: String) = Unit
}


case class GhOrganization(login: String)

case class GhRepository(name: String, id: Long, html_url: String)


case class GhTeam(name: String, id: Long)

object GhTeam {
  implicit val formats = Json.format[GhTeam]
}


object GhOrganization {
  implicit val formats = Json.format[GhOrganization]
}

object GhRepository {
  implicit val formats = Json.format[GhRepository]
}

trait GithubHttp extends CredentialsFinder {

  val log = new Logger()

  def cred: ServiceCredentials

  lazy val host = cred.host

  private val asyncBuilder: Builder = new Builder()
  private val tp: ExecutorService = Executors.newCachedThreadPool()
  asyncBuilder.setExecutorService(tp)

  private val builder: NingAsyncHttpClientConfigBuilder =
    new NingAsyncHttpClientConfigBuilder(
      config = new DefaultWSClientConfig(/*connectionTimeout = Some(120 * 1000)*/),
      builder = asyncBuilder)


  val ws = new NingWSClient(builder.build())


  def close(): Unit = {
    ws.close()
    tp.shutdown()
  }

  def buildCall(method: String, url: String, body: Option[JsValue] = None): WSRequestHolder = {
    log.debug(s"github client_id ${cred.user.takeRight(5)}")
    log.debug(s"github client_secret ${cred.pass.takeRight(5)}")

    val req = ws.url(url)
      .withMethod(method)
      .withAuth(cred.user, cred.pass, WSAuthScheme.BASIC)
      .withQueryString("client_id" -> cred.user, "client_secret" -> cred.pass)
      .withHeaders("content-type" -> "application/json")

    body.map { b =>
      req.withBody(b)
    }.getOrElse(req)
  }

  def callAndWait(req: WSRequestHolder): WSResponse = {

    log.info(s"${req.method} with ${req.url}")

    val result: WSResponse = Await.result(req.execute(), Duration.apply(1, TimeUnit.MINUTES))

    log.info(s"result ${result.status} - ${result.statusText} - ${result.body}")

    result
  }

  def get[A](url: String)(implicit r: Reads[A]): Future[A] = {
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

object GithubHttp extends GithubHttp {

  val enterpriseGithub = "github.tools.tax.service.gov.uk"

  val cred: ServiceCredentials = new File(System.getProperty("user.home"), ".github").listFiles()
    .flatMap { c => findGithubCredsInFile(c.toPath) }
    .find(_.host == enterpriseGithub).getOrElse(throw new IllegalAccessException(s"didn't find credentials for $enterpriseGithub"))

}
