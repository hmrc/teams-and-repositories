/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.connector

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class ServiceConfigsConnectorSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with WireMockSupport
    with HttpClientV2Support:

  given HeaderCarrier = HeaderCarrier()

  private val connector: ServiceConfigsConnector =
    ServiceConfigsConnector(
      httpClientV2,
      ServicesConfig(
        Configuration(
          "microservice.services.service-configs.port" -> wireMockPort,
          "microservice.services.service-configs.host" -> wireMockHost
        )
      )
    )

  "getFrontendServices" should:
    "return the name of services that have frontend route type" in:
      stubFor(
        get(urlEqualTo(s"/service-configs/routes?routeType=frontend"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  |  {
                  |    "serviceName": "service-1",
                  |    "path": "/test-1",
                  |    "ruleConfigurationUrl": "https://github.com/hmrc/...conf#L206",
                  |    "isRegex": false,
                  |    "routeType": "frontend",
                  |    "environment": "production"
                  |  },
                  |  {
                  |    "serviceName": "service-1",
                  |    "path": "/test-2",
                  |    "ruleConfigurationUrl": "https://github.com/hmrc/...conf#L206",
                  |    "isRegex": false,
                  |    "routeType": "frontend",
                  |    "environment": "production"
                  |  },
                  |  {
                  |    "serviceName": "service-2",
                  |    "path": "/test-2",
                  |    "ruleConfigurationUrl": "https://github.com/hmrc/...conf#L103",
                  |    "isRegex": false,
                  |    "routeType": "frontend",
                  |    "environment": "qa"
                  |  }
                  |]
                  |""".stripMargin
              )
          )
      )

      connector.getFrontendServices().futureValue should contain theSameElementsAs Set("service-1", "service-2")

  "getAdminFrontendServices" should :
    "return the name of services that have admin frontend route type" in :
      stubFor(
        get(urlEqualTo(s"/service-configs/routes?routeType=adminfrontend"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  |  {
                  |    "serviceName": "service-1",
                  |    "path": "/test-1",
                  |    "ruleConfigurationUrl": "https://github.com/hmrc/...conf#L206",
                  |    "isRegex": false,
                  |    "routeType": "adminfrontend",
                  |    "environment": "production"
                  |  },
                  |  {
                  |    "serviceName": "service-1",
                  |    "path": "/test-2",
                  |    "ruleConfigurationUrl": "https://github.com/hmrc/...conf#L206",
                  |    "isRegex": false,
                  |    "routeType": "adminfrontend",
                  |    "environment": "production"
                  |  },
                  |  {
                  |    "serviceName": "service-2",
                  |    "path": "/test-2",
                  |    "ruleConfigurationUrl": "https://github.com/hmrc/...conf#L103",
                  |    "isRegex": false,
                  |    "routeType": "adminfrontend",
                  |    "environment": "qa"
                  |  }
                  |]
                  |""".stripMargin
              )
          )
      )

      connector.getAdminFrontendServices().futureValue should contain theSameElementsAs Set("service-1", "service-2")

end ServiceConfigsConnectorSpec
