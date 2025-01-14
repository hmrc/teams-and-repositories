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

package uk.gov.hmrc.teamsandrepositories.connector

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.teamsandrepositories.model.User

import scala.concurrent.ExecutionContext.Implicits.global


class UserManagementConnectorSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with WireMockSupport
  with HttpClientV2Support:

  given HeaderCarrier = HeaderCarrier()

  private val connector: UserManagementConnector =
    UserManagementConnector(
      httpClientV2,
      ServicesConfig(
        Configuration(
          "microservice.services.user-management.port" -> wireMockPort,
          "microservice.services.user-management.host" -> wireMockHost
        )
      )
    )

  "UserManagementConnector.getUsersForTeam" should:
    "return a list of users in a team" in {
      stubFor(
        get(urlEqualTo("/user-management/users?team=TestTeam"))
          .willReturn(
            aResponse()
              .withBody("""[
                {
                   "displayName" : "Joe Bloggs",
                   "familyName" : "Bloggs",
                   "givenName" : "Joe",
                   "organisation" : "MDTP",
                   "primaryEmail" : "joe.bloggs@digital.hmrc.gov.uk",
                   "username" : "joe.bloggs",
                   "githubUsername" : "joebloggs",
                   "github" : "https://github.com/joebloggs",
                   "phoneNumber" : "07123456789",
                   "role" : "user",
                   "teamNames" : [
                        "TestTeam"
                   ]
                },
                {
                   "displayName" : "Jane Doe",
                   "familyName" : "Doe",
                   "givenName" : "Jane",
                   "organisation" : "MDTP",
                   "primaryEmail" : "jane.doe@digital.hmrc.gov.uk",
                   "username" : "jane.doe",
                   "githubUsername" : "janedoe",
                   "github" : "https://github.com/janedoe",
                   "phoneNumber" : "07123456789",
                   "role" : "user",
                   "teamNames" : [
                        "TestTeam"
                   ]
                }
              ]""".stripMargin)
          )
      )

      connector.getUsersForTeam("TestTeam").futureValue should contain theSameElementsAs
        Seq(
          User(
            githubUsername = Some("joebloggs")
          ),
          User(
            githubUsername = Some("janedoe")
          )
        )
    }
