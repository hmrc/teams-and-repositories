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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, BeforeAndAfterEach, WordSpec}

class GithubHttpSpec extends WordSpec with BeforeAndAfterEach with ScalaFutures with Matchers with DefaultPatienceConfig{

  val testHost = "localhost"
  val port = 7654

  val orgsUrl = "/api/v3/user/orgs"
  val teamsUrl = "/api/v3/orgs/HMRC/teams?per_page=100"
  val reposUrl = "/api/v3/orgs/DDCN/teams?per_page=100"

  val wireMockServer = new WireMockServer(port)

  val gh = new GithubHttp{
    override val cred: ServiceCredentials = ServiceCredentials(s"$testHost:$port","","")
  }


  override def beforeEach {
    wireMockServer.start()
    WireMock.configureFor(testHost, port)

  }

  override def afterEach {
    wireMockServer.stop()
  }

  "GitHubHttp" should {


    "Return a list of organisations" in {

      stubFor(
        get(urlEqualTo(s"$orgsUrl?client_id=&client_secret=")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""[
                        |  {
                        |    "login": "HMRC",
                        |    "id": 108,
                        |    "url": "http://example.com/api/v3/orgs/HMRC",
                        |    "repos_url": "http://example.com/api/v3/orgs/HMRC/repos",
                        |    "events_url": "http://example.com/api/v3/orgs/HMRC/events",
                        |    "members_url": "http://example.com/api/v3/orgs/HMRC/members{/member}",
                        |    "public_members_url": "http://example.com/api/v3/orgs/HMRC/public_members{/member}",
                        |    "avatar_url": "http://example.com/avatars/u/108?",
                        |    "description": ""
                        |  }
                        |]
                        |""".stripMargin)))

      val result = gh.get[List[GhOrganization]](s"http://$testHost:$port$orgsUrl")

      result.futureValue.length shouldBe 1
      result.futureValue.head.login shouldBe "HMRC"



    }

    "Return a list of teams for an organisation" in {

      //val result = gh.get[GhOrganization](teamsUrl)

    }

    "Return a list of repositories for a team" in {

      //val result = gh.get[GhOrganization](reposUrl)

    }

  }

}
