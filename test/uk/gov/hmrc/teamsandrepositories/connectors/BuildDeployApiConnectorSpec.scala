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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.teamsandrepositories.config.BuildDeployApiConfig

import scala.concurrent.ExecutionContext.Implicits.global
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class BuildDeployApiConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with WireMockSupport
     with HttpClientV2Support
     with IntegrationPatience {

  "getBuildJobs" should {

    "Invoke the API and return build jobs if successful" in {
      stubFor(
        post("/get-build-jobs")
          .willReturn(aResponse().withBody(buildJobResponseJson))
      )

      def buildJob1(repoName: String) = BuildDeployApiConnector.BuildJob(
        jobName    = repoName,
        jenkinsUrl = s"https://build.tax.service.gov.uk/job/Centre%20Technical%20Leads/job/$repoName/",
        jobType    = BuildDeployApiConnector.JobType.Job
      )

      def buildJob2(repoName: String) = BuildDeployApiConnector.BuildJob(
        jobName    = s"$repoName-pipeline",
        jenkinsUrl = s"https://build.tax.service.gov.uk/job/Centre%20Technical%20Leads/job/$repoName-pipeline/",
        jobType    = BuildDeployApiConnector.JobType.Pipeline
      )

      connector.getBuildJobsDetails().futureValue shouldBe List(
        BuildDeployApiConnector.Detail("test-repo-1", List(buildJob1("test-repo-1"), buildJob2("test-repo-1")))
      , BuildDeployApiConnector.Detail("test-repo-2", List(buildJob1("test-repo-2"), buildJob2("test-repo-2")))
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/get-build-jobs"))
      )
    }
  }

  "enableBranchProtection" should {

    "Invoke the API and return unit if successful" in {
      stubFor(
        post("/set-branch-protection")
          .withRequestBody(equalToJson(branchProtectionRequestJson))
          .willReturn(aResponse().withBody(
            """
              [
                { "success": true,
                  "message": "some message"
                }
              ]
            """
          ))
      )

      connector.enableBranchProtection("some-repo").futureValue

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/set-branch-protection"))
          .withRequestBody(equalToJson(branchProtectionRequestJson))
      )
    }

    "Invoke the API and raise an error if unsuccessful" in {
      stubFor(
        post("/set-branch-protection")
          .withRequestBody(equalToJson(branchProtectionRequestJson))
          .willReturn(aResponse().withBody(
            """
              [
                { "success": false,
                  "message": "some error message"
                }
              ]
            """
          ))
      )

      val error =
        connector
          .enableBranchProtection("some-repo")
          .failed
          .futureValue

      error.getMessage should include ("some error message")

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/set-branch-protection"))
          .withRequestBody(equalToJson(branchProtectionRequestJson))
      )
    }
  }

  private lazy val buildJobResponseJson: String =
    """{
      |    "success": true,
      |    "message": "",
      |    "details": [
      |        {
      |            "repository_name": "test-repo-1",
      |            "build_jobs": [
      |                {
      |                    "name": "Centre Technical Leads/test-repo-1",
      |                    "url": "https://build.tax.service.gov.uk/job/Centre%20Technical%20Leads/job/test-repo-1/",
      |                    "type": "job"
      |                },
      |                {
      |                    "name": "Centre Technical Leads/test-repo-1-pipeline",
      |                    "url": "https://build.tax.service.gov.uk/job/Centre%20Technical%20Leads/job/test-repo-1-pipeline/",
      |                    "type": "pipeline"
      |                }
      |            ]
      |        },
      |        {
      |            "repository_name": "test-repo-2",
      |            "build_jobs": [
      |                {
      |                    "name": "Centre Technical Leads/test-repo-2",
      |                    "url": "https://build.tax.service.gov.uk/job/Centre%20Technical%20Leads/job/test-repo-2/",
      |                    "type": "job"
      |                },
      |                {
      |                    "name": "Centre Technical Leads/test-repo-2-pipeline",
      |                    "url": "https://build.tax.service.gov.uk/job/Centre%20Technical%20Leads/job/test-repo-2-pipeline/",
      |                    "type": "pipeline"
      |                }
      |            ]
      |        }
      |    ]
      |}
      |""".stripMargin

  private lazy val branchProtectionRequestJson =
    """
      { "repository_names": "some-repo",
        "set_branch_protection_rule": true
      }
    """

  private lazy val connector =
    new BuildDeployApiConnector(httpClientV2, config)

  private lazy val config =
    new BuildDeployApiConfig(
      new ServicesConfig(
        Configuration(
          "microservice.services.platops-bnd-api.port" -> wireMockPort,
          "microservice.services.platops-bnd-api.host" -> wireMockHost
        )
      )
    )
}
