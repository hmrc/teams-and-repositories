/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.{HttpClientSupport, WireMockSupport}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits._

class GithubConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with OptionValues
     with WireMockSupport
     with HttpClientSupport {

  val token = "token"

  val githubConfig =
    new GithubConfig(
      Configuration(
        "github.open.api.user"     -> "user",
        "github.open.api.key"      -> token,
        "github.open.api.url"      -> wireMockUrl,
        "github.open.api.rawurl"   -> s"$wireMockUrl/raw",
        "ratemetrics.githubtokens" -> List()
      )
    )

  implicit val headerCarrier = HeaderCarrier()
  val connector = new GithubConnector(githubConfig, httpClient)

  "GithubConnector.getFileContent" should {
    "return fileContent" in {
      val path        = "path"
      val fileContent = "fileContent"
      stubFor(
        get(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .willReturn(aResponse().withBody(fileContent))
      )

      connector.getFileContent(repo, path).futureValue.value shouldBe fileContent

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "return None when file does not exist" in {
      val path        = "path"
      stubFor(
        get(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getFileContent(repo, path).futureValue shouldBe None

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "fail when there is an error" in {
      val path        = "path"
      stubFor(
        get(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.getFileContent(repo, path).failed.futureValue shouldBe an[UpstreamErrorResponse]
    }
  }

  "GithubConnector.getTeams" should {
    "return teams" in {
      stubFor(
        get(urlPathEqualTo("/orgs/hmrc/teams"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  {"id": 1, "name": "A"},
                  {"id": 2, "name": "B"}
                ]"""
              )
              .withHeader("link", s"""<$wireMockUrl/nextPage>; rel="next", <$wireMockUrl/lastPage>; rel="last"""")
          )
      )

      stubFor(
        get(urlPathEqualTo("/nextPage"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  {"id": 3, "name": "C"}
                ]"""
              )
          )
      )

      connector.getTeams().futureValue shouldBe List(GhTeam(1, "A"), GhTeam(2, "B"), GhTeam(3, "C"))

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/orgs/hmrc/teams"))
          .withQueryParam("per_page", equalTo("100"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/nextPage"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "throw ratelimit error" in {
      stubFor(
        get(urlPathEqualTo("/orgs/hmrc/teams"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody("asdsad api rate limit exceeded dsadsa")
          )
      )

      connector.getTeams().failed.futureValue shouldBe an[APIRateLimitExceededException]
    }
  }

  val reposJson1 =
    """[
       {
         "id"            : 1,
         "name"          : "n1",
         "description"   : "d1",
         "html_url"      : "url1",
         "fork"          : false,
         "created_at"    : "2019-04-01T11:41:33Z",
         "pushed_at"     : "2019-04-02T11:41:33Z",
         "private"       : true,
         "language"      : "l1",
         "archived"      : false,
         "default_branch": "b1"
       },
       {
         "id"            : 2,
         "name"          : "n2",
         "description"   : "d2",
         "html_url"      : "url2",
         "fork"          : false,
         "created_at"    : "2019-04-03T11:41:33Z",
         "pushed_at"     : "2019-04-04T11:41:33Z",
         "private"       : false,
         "language"      : "l2",
         "archived"      : true,
         "default_branch": "b2"
       }
    ]"""

  val reposJson2 =
    """[
         {
           "id"            : 3,
           "name"          : "n3",
           "html_url"      : "url3",
           "fork"          : true,
           "created_at"    : "2019-04-05T11:41:33Z",
           "pushed_at"     : "2019-04-06T11:41:33Z",
           "private"       : true,
           "archived"      : false,
           "default_branch": "b3"
         }
      ]"""

  val reposJson3 =
    """[
         {
           "id"            : 4,
           "name"          : "n4",
           "html_url"      : "url4",
           "fork"          : true,
           "created_at"    : "2019-04-07T11:41:33Z",
           "pushed_at"     : "2019-04-08T11:41:33Z",
           "private"       : true,
           "archived"      : false,
           "default_branch": "b4"
         }
      ]"""

  val repos =
    List(
      GhRepository(
        id             = 1,
        name           = "n1",
        description    = Some("d1"),
        htmlUrl        = "url1",
        fork           = false,
        createdDate    = Instant.parse("2019-04-01T11:41:33Z"),
        lastActiveDate = Instant.parse("2019-04-02T11:41:33Z"),
        isPrivate      = true,
        language       = Some("l1"),
        isArchived     = false,
        defaultBranch  = "b1"
      ),
      GhRepository(
        id             = 2,
        name           = "n2",
        description    = Some("d2"),
        htmlUrl        = "url2",
        fork           = false,
        createdDate    = Instant.parse("2019-04-03T11:41:33Z"),
        lastActiveDate = Instant.parse("2019-04-04T11:41:33Z"),
        isPrivate      = false,
        language       = Some("l2"),
        isArchived     = true,
        defaultBranch  = "b2"
      ),
      GhRepository(
        id             = 3,
        name           = "n3",
        description    = None,
        htmlUrl        = "url3",
        fork           = true,
        createdDate    = Instant.parse("2019-04-05T11:41:33Z"),
        lastActiveDate = Instant.parse("2019-04-06T11:41:33Z"),
        isPrivate      = true,
        language       = None,
        isArchived     = false,
        defaultBranch  = "b3"
      ),
      GhRepository(
        id             = 4,
        name           = "n4",
        description    = None,
        htmlUrl        = "url4",
        fork           = true,
        createdDate    = Instant.parse("2019-04-07T11:41:33Z"),
        lastActiveDate = Instant.parse("2019-04-08T11:41:33Z"),
        isPrivate      = true,
        language       = None,
        isArchived     = false,
        defaultBranch  = "b4"
      )
    )

  "GithubConnector.getReposForTeam" should {
    "return repos" in {
      val team = GhTeam(id = 1, name = "A Team")
      stubFor(
        get(urlPathEqualTo(s"/orgs/hmrc/teams/a-team/repos"))
          .willReturn(
            aResponse()
              .withBody(reposJson1)
              .withHeader("link", s"""<$wireMockUrl/nextPage>; rel="next", <$wireMockUrl/lastPage>; rel="last"""")
          )
      )

      stubFor(
        get(urlPathEqualTo(s"/nextPage"))
          .willReturn(
            aResponse()
              .withBody(reposJson2)
              .withHeader("link", s"""<$wireMockUrl/lastPage>; rel="last"""")
          )
      )
      stubFor(
        get(urlPathEqualTo("/lastPage"))
          .willReturn(aResponse().withBody(reposJson3))
      )

      connector.getReposForTeam(team).futureValue shouldBe repos

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/orgs/hmrc/teams/a-team/repos"))
          .withQueryParam("per_page", equalTo("100"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/nextPage"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/lastPage"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }
  }

  "GithubConnector.getRepos" should {
    "return repos" in {
      stubFor(
        get(urlPathEqualTo(s"/orgs/hmrc/repos"))
          .willReturn(
            aResponse()
              .withBody(reposJson1)
              .withHeader("link", s"""<$wireMockUrl/nextPage>; rel="next", <$wireMockUrl/lastPage>; rel="last"""")
          )
      )

      stubFor(
        get(urlPathEqualTo(s"/nextPage"))
          .willReturn(
            aResponse()
              .withBody(reposJson2)
              .withHeader("link", s"""<$wireMockUrl/lastPage>; rel="last"""")
          )
      )
      stubFor(
        get(urlPathEqualTo("/lastPage"))
          .willReturn(aResponse().withBody(reposJson3))
      )

      connector.getRepos().futureValue shouldBe repos

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/orgs/hmrc/repos"))
          .withQueryParam("per_page", equalTo("100"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/nextPage"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/lastPage"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }
  }

  "GithubConnector.hasTags" should {
    "return true when there are tags" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/my-repo/tags"))
          .willReturn(
            aResponse()
              .withBody("""[{"name": "tag1"}]""")
          )
      )

      connector.hasTags(repo).futureValue shouldBe true

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/repos/hmrc/my-repo/tags"))
          .withQueryParam("per_page", equalTo("1"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "return false when there are no tags" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/my-repo/tags"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.hasTags(repo).futureValue shouldBe false
    }
  }

  "GithubConnector.existsContent" should {
    "return true when there is content" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/my-repo/contents/path"))
          .willReturn(
            aResponse()
              .withBody("""{content:"any-content"}""")
          )
      )

      connector.existsContent(repo, "path").futureValue shouldBe true

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/repos/hmrc/my-repo/contents/path"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "return false when there is no content" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/my-repo/contents/path"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.existsContent(repo, "path").futureValue shouldBe false
    }

    "fail when there is an error" in {
      stubFor(
        get(urlPathEqualTo("/repos/hmrc/my-repo/contents/path"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.existsContent(repo, "path").failed.futureValue shouldBe an[UpstreamErrorResponse]
    }
  }

  "GithubConnector.getRateLimitMetrics" should {
    "return rate limit metrics" in {
      val token = "t"
      stubFor(
        get(urlPathEqualTo("/rate_limit"))
          .willReturn(
            aResponse()
              .withBody(
                """{
                  "rate": {
                    "limit"     : 1,
                    "remaining" : 2,
                    "reset"     : 3
                  }
                }"""
              )
          )
      )

      connector.getRateLimitMetrics(token).futureValue shouldBe RateLimitMetrics(
        limit     = 1,
        remaining = 2,
        reset     = 3
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/rate_limit"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }
  }

  val repo =
    GhRepository(
      id             = 1,
      name           = "my-repo",
      description    = Some("d1"),
      htmlUrl        = "url1",
      fork           = false,
      createdDate    = Instant.parse("2019-04-01T11:41:33Z"),
      lastActiveDate = Instant.parse("2019-04-02T11:41:33Z"),
      isPrivate      = true,
      language       = Some("l1"),
      isArchived     = false,
      defaultBranch  = "b1"
    )
}
