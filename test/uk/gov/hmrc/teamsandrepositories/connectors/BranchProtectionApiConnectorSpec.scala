/*
 * Copyright 2022 HM Revenue & Customs
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

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.test.{HttpClientSupport, WireMockSupport}
import uk.gov.hmrc.teamsandrepositories.config.BranchProtectionApiConfig
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.tomakehurst.wiremock.client.WireMock._

class BranchProtectionApiConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with WireMockSupport
     with HttpClientSupport
     with IntegrationPatience {

  "setBranchProtection" should {

    "Invoke the API and return unit if successful" in {
      stubFor(
        post("/v1/UpdateGithubDefaultBranchProtection")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withBody(
            """
              |[
              |  { "success": true,
              |    "message": "some message"
              |  }
              |]
              |""".stripMargin
          ))
      )

      connector.setBranchProtection("some-repo").futureValue

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/v1/UpdateGithubDefaultBranchProtection"))
          .withRequestBody(equalToJson(requestJson))
      )
    }

    "Invoke the API and raise an error if unsuccessful" in {
      stubFor(
        post("/v1/UpdateGithubDefaultBranchProtection")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withBody(
            """
              |[
              |  { "success": false,
              |    "message": "some error message"
              |  }
              |]
              |""".stripMargin
          ))
      )

      val error =
        connector
          .setBranchProtection("some-repo")
          .failed
          .futureValue

      error.getMessage should include ("some error message")

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/v1/UpdateGithubDefaultBranchProtection"))
          .withRequestBody(equalToJson(requestJson))
      )
    }
  }

  private lazy val requestJson =
    """
      |{ "repository_names": "some-repo",
      |  "set_branch_protection_rule": true
      |}
      |""".stripMargin

  private lazy val connector =
    new BranchProtectionApiConnector(httpClient, awsCredentialsProvider, config)

  private lazy val config =
    new BranchProtectionApiConfig(
      Configuration(
        "branch-protection-api.url"        -> wireMockUrl,
        "branch-protection-api.aws-region" -> "eu-west-2",
      )
    )

  private lazy val awsCredentialsProvider =
    new AWSStaticCredentialsProvider(new BasicAWSCredentials("test","test"))
}
