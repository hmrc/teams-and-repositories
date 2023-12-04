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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import scala.concurrent.ExecutionContext.Implicits.global

class BuildJobConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with OptionValues
     with WireMockSupport
     with HttpClientV2Support {

  val token = "token"

  private val connector = {
    val githubConfig = new GithubConfig(Configuration.from(Map(
      "github.open.api.rawurl"    -> s"$wireMockUrl/raw",
      "github.open.api.key"       -> "TOKEN",
      "github.open.api.url"       -> wireMockUrl,
      "ratemetrics.githubtokens"  -> Nil
    )))
    new BuildJobsConnector(githubConfig, httpClientV2)
  }


  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  "BuildJobConnector.getBuildjobFiles" should {
    "return jobfiles" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/build-jobs/contents/jobs/live"))
          .willReturn(aResponse().withBody(
            """[
             {"name": "file1"},
             {"name": "file2"}
             ]
            """
          ))
      )

      connector.getBuildjobFiles().futureValue shouldBe List(
        BuildJobFilename("file1"),
        BuildJobFilename("file2")
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/repos/hmrc/build-jobs/contents/jobs/live"))
          .withHeader("Authorization", equalTo("token TOKEN"))
      )
    }
  }

  "BuildJobConnector.getBuildjobFileContent" should {
    "return jobfiles" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/build-jobs/contents/jobs/live/file1"))
          .willReturn(aResponse().withBody("content"))
      )

      connector.getBuildjobFileContent(BuildJobFilename("file1")).futureValue shouldBe "content"

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/repos/hmrc/build-jobs/contents/jobs/live/file1"))
          .withHeader("Authorization", equalTo("token TOKEN"))
      )
    }
  }
}
