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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector.{getReposForTeamQuery, getReposQuery}

import java.time.Instant

class GithubConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with OptionValues
     with WireMockSupport
     with GuiceOneAppPerSuite {

  val token = "token"

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "github.open.api.user"     -> "user",
        "github.open.api.key"      -> token,
        "github.open.api.url"      -> wireMockUrl,
        "github.open.api.rawurl"   -> s"$wireMockUrl/raw",
        "ratemetrics.githubtokens" -> List(),
        "metrics.jvm"              -> false
      )
      .build()

  private val connector = app.injector.instanceOf[GithubConnector]

  implicit val headerCarrier = HeaderCarrier()

  "GithubConnector.getFileContent" should {
    "return fileContent" in {
      val path        = "path"
      val fileContent = "fileContent"

      stubFor(
        get(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .willReturn(aResponse().withBody(fileContent))
      )

      connector.getFileContent(repo, path).futureValue.value shouldBe fileContent

      wireMockServer.verify(1,
        getRequestedFor(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "return None when file does not exist" in {
      val path = "path"

      stubFor(
        get(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getFileContent(repo, path).futureValue shouldBe None

      wireMockServer.verify(1,
        getRequestedFor(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "fail when there is an error" in {
      val path = "path"

      stubFor(
        get(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
          .willReturn(aResponse().withStatus(500))
      )

      connector.getFileContent(repo, path).failed.futureValue shouldBe an[UpstreamErrorResponse]

      wireMockServer.verify(6, // with retries
        getRequestedFor(urlPathEqualTo(s"/raw/hmrc/my-repo/b1/$path"))
      )
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

      connector.getTeams().futureValue shouldBe List(
        GhTeam(1, "A"),
        GhTeam(2, "B"),
        GhTeam(3, "C")
      )

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

      connector.getTeams().failed.futureValue shouldBe an[ApiRateLimitExceededException]
    }
  }

  "GithubConnector.getTeamDetail" should {
    val team = GhTeam(1, "A")

    "return team detail" in {
      stubFor(
        get(urlPathEqualTo("/orgs/hmrc/teams/a"))
          .willReturn(
            aResponse()
              .withBody(
                """{"id": 1, "name": "A", "created_at": "2019-03-01T12:00:00Z"}"""
              )
          )
      )

      connector.getTeamDetail(team).futureValue shouldBe Some(
        GhTeamDetail(1, "A", Instant.parse("2019-03-01T12:00:00Z"))
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/orgs/hmrc/teams/a"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }

    "return None if not found" in {
      stubFor(
        get(urlPathEqualTo("/orgs/hmrc/teams/a"))
          .willReturn(aResponse().withStatus(404))
      )

      connector.getTeamDetail(team).futureValue shouldBe None

      wireMockServer.verify(getRequestedFor(urlPathEqualTo("/orgs/hmrc/teams/a")))
    }

    "throw ratelimit error" in {
      stubFor(
        get(urlPathEqualTo("/orgs/hmrc/teams/a"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody("asdsad api rate limit exceeded dsadsa")
          )
      )

      connector.getTeamDetail(team).failed.futureValue shouldBe an[ApiRateLimitExceededException]
    }

    "throw abuse detected error" in {
      stubFor(
        get(urlPathEqualTo("/orgs/hmrc/teams/a"))
          .willReturn(
            aResponse()
              .withStatus(403)
              .withBody("You have triggered an abuse detection mechanism. Please wait a few minutes before you try again.")
          )
      )

      connector.getTeamDetail(team).failed.futureValue shouldBe an[ApiAbuseDetectedException]
    }
  }
  val reposJson1 =
    """[
       {
         "databaseId": 1,
         "name": "n1",
         "description": "d1",
         "url": "url1",
         "isFork": false,
         "createdAt": "2019-04-01T11:41:33Z",
         "pushedAt": "2019-04-02T11:41:33Z",
         "isPrivate": true,
         "primaryLanguage": {
           "name": "l1"
         },
         "isArchived": false,
         "defaultBranchRef": {
           "name": "b1",
           "branchProtectionRule": {
             "requiresApprovingReviews": true,
             "dismissesStaleReviews": true
           }
         }
       },
       {
         "databaseId": 2,
         "name": "n2",
         "description": "d2",
         "url": "url2",
         "isFork": false,
         "createdAt": "2019-04-03T11:41:33Z",
         "pushedAt": "2019-04-04T11:41:33Z",
         "isPrivate": false,
         "primaryLanguage": {
           "name": "l2"
         },
         "isArchived": true,
         "defaultBranchRef": {
           "name": "b2",
           "branchProtectionRule": {
             "requiresApprovingReviews": true,
             "dismissesStaleReviews": true
           }
         }
       }
    ]"""

  val reposJson2 =
    """[
         {
           "databaseId": 3,
           "name": "n3",
           "url": "url3",
           "isFork": true,
           "createdAt": "2019-04-05T11:41:33Z",
           "pushedAt": "2019-04-06T11:41:33Z",
           "isPrivate": true,
           "isArchived": false,
           "defaultBranchRef": {
             "name": "b3"
           }
         }
      ]"""

  val reposForTeamJson1 =
    s"""
      {
        "data": {
          "organization": {
            "team": {
              "repositories": {
                "pageInfo": {
                  "endCursor": "cursor-1"
                },
                "nodes": $reposJson1
              }
            }
          }
        }
      }
     """

  val reposForTeamJson2 =
    s"""
      {
        "data": {
          "organization": {
            "team": {
              "repositories": {
                "pageInfo": {},
                "nodes": $reposJson2
              }
            }
          }
        }
      }
     """

  val allReposJson1 =
    s"""
      {
        "data": {
          "organization": {
            "repositories": {
              "pageInfo": {
                "endCursor": "cursor-1"
              },
              "nodes": $reposJson1
            }
          }
        }
      }
     """

  val allReposJson2 =
    s"""
      {
        "data": {
          "organization": {
            "repositories": {
              "pageInfo": {},
              "nodes": $reposJson2
            }
          }
        }
      }
     """

  val repos =
    List(
      GhRepository(
        name             = "n1",
        description      = Some("d1"),
        htmlUrl          = "url1",
        fork             = false,
        createdDate      = Instant.parse("2019-04-01T11:41:33Z"),
        lastActiveDate   = Instant.parse("2019-04-02T11:41:33Z"),
        isPrivate        = true,
        language         = Some("l1"),
        isArchived       = false,
        defaultBranch    = "b1",
        branchProtection = Some(GhBranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true))
      ),
      GhRepository(
        name             = "n2",
        description      = Some("d2"),
        htmlUrl          = "url2",
        fork             = false,
        createdDate      = Instant.parse("2019-04-03T11:41:33Z"),
        lastActiveDate   = Instant.parse("2019-04-04T11:41:33Z"),
        isPrivate        = false,
        language         = Some("l2"),
        isArchived       = true,
        defaultBranch    = "b2",
        branchProtection = Some(GhBranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true))
      ),
      GhRepository(
        name             = "n3",
        description      = None,
        htmlUrl          = "url3",
        fork             = true,
        createdDate      = Instant.parse("2019-04-05T11:41:33Z"),
        lastActiveDate   = Instant.parse("2019-04-06T11:41:33Z"),
        isPrivate        = true,
        language         = None,
        isArchived       = false,
        defaultBranch    = "b3",
        branchProtection = None
      )
    )

  "GithubConnector.getReposForTeam" should {
    "return repos" in {
      val team =
        GhTeam(id = 1, name = "A Team")

      val query1 =
        getReposForTeamQuery
          .withVariable("team", JsString(team.githubSlug))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query1.asJsonString))
          .willReturn(aResponse().withBody(reposForTeamJson1))
      )

      val query2 =
        query1
          .withVariable("cursor", JsString("cursor-1"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query2.asJsonString))
          .willReturn(aResponse().withBody(reposForTeamJson2))
      )

      connector.getReposForTeam(team).futureValue shouldBe repos

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query1.asJsonString))
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query2.asJsonString))
      )
    }

    "return an empty list when a team does not exist" in {
      val team =
        GhTeam(id = 1, name = "A Team")

      val query =
        getReposForTeamQuery
          .withVariable("team", JsString(team.githubSlug))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
          .willReturn(
            aResponse()
              .withBody(
                """
                  {
                    "data": {
                      "organization": {
                        "team": null
                      }
                    }
                  }
                """
              )
          )
      )

      connector.getReposForTeam(team).futureValue shouldBe empty

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
      )
    }
  }

  "GithubConnector.getRepos" should {
    "return repos" in {
      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getReposQuery.asJsonString))
          .willReturn(aResponse().withBody(allReposJson1))
      )

      val query2 =
        getReposQuery
          .withVariable("cursor", JsString("cursor-1"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query2.asJsonString))
          .willReturn(aResponse().withBody(allReposJson2))
      )

      connector.getRepos().futureValue shouldBe repos

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getReposQuery.asJsonString))
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query2.asJsonString))
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
      name             = "my-repo",
      description      = Some("d1"),
      htmlUrl          = "url1",
      fork             = false,
      createdDate      = Instant.parse("2019-04-01T11:41:33Z"),
      lastActiveDate   = Instant.parse("2019-04-02T11:41:33Z"),
      isPrivate        = true,
      language         = Some("l1"),
      isArchived       = false,
      defaultBranch    = "b1",
      branchProtection = None
    )
}
