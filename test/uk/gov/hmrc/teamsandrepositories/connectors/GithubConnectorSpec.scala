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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector.{getRepoQuery, getReposForTeamQuery, getReposQuery, getTeamsQuery}

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
        "metrics.jvm"              -> false,
        "jenkins.username" -> "",
        "jenkins.token" -> "",
        "jenkins.url" -> ""
      )
      .build()

  private val connector = app.injector.instanceOf[GithubConnector]

  implicit val headerCarrier = HeaderCarrier()

  val createdAt =
    Instant.parse("2019-03-01T12:00:00Z")

  "GithubConnector.getTeams" should {
    "return teams" in {
      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getTeamsQuery.asJsonString))
          .willReturn(aResponse().withBody(
            """
             {
               "data": {
                 "organization": {
                   "teams": {
                     "pageInfo": {
                       "endCursor": "cursor-1"
                     },
                     "nodes": [
                       {
                         "name": "A",
                         "createdAt": "2019-03-01T12:00:00Z"
                       },
                       {
                         "name": "B",
                         "createdAt": "2019-03-01T12:00:00Z"
                       }
                     ]
                   }
                 }
               }
             }
            """
          ))
      )

      val query2 =
        getTeamsQuery
          .withVariable("cursor", JsString("cursor-1"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query2.asJsonString))
          .willReturn(aResponse().withBody(
            """
             {
               "data": {
                 "organization": {
                   "teams": {
                     "pageInfo": {},
                     "nodes": [
                       {
                         "name": "C",
                         "createdAt": "2019-03-01T12:00:00Z"
                       }
                     ]
                   }
                 }
               }
             }
            """
          ))
      )

      connector.getTeams().futureValue shouldBe List(
        GhTeam("A", createdAt),
        GhTeam("B", createdAt),
        GhTeam("C", createdAt)
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getTeamsQuery.asJsonString))
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query2.asJsonString))
      )
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
             "dismissesStaleReviews": true,
             "requiresCommitSignatures": true
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
             "dismissesStaleReviews": true,
             "requiresCommitSignatures": true
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

  val singleRepoJson =
    s"""
      {
        "data": {
          "organization": {
            "repository": {
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
                  "dismissesStaleReviews": true,
                  "requiresCommitSignatures": true
                }
              }
            }
          }
        }
      }
     """

  val dummyRepoTypeHeuristics =
    RepoTypeHeuristics(
      prototypeInName     = false,
      testsInName         = false,
      hasApplicationConf  = false,
      hasDeployProperties = false,
      hasProcfile         = false,
      hasSrcMainScala     = false,
      hasSrcMainJava      = false,
      hasTags             = false
    )

  val repo1 =
    GhRepository(
      name               = "n1",
      description        = Some("d1"),
      htmlUrl            = "url1",
      fork               = false,
      createdDate        = Instant.parse("2019-04-01T11:41:33Z"),
      pushedAt           = Instant.parse("2019-04-02T11:41:33Z"),
      isPrivate          = true,
      language           = Some("l1"),
      isArchived         = false,
      defaultBranch      = "b1",
      branchProtection   = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReview = true, requiresCommitSignatures = true)),
      repositoryYamlText = None,
      repoTypeHeuristics = dummyRepoTypeHeuristics
    )

  val repos =
    List(
      repo1,
      GhRepository(
        name               = "n2",
        description        = Some("d2"),
        htmlUrl            = "url2",
        fork               = false,
        createdDate        = Instant.parse("2019-04-03T11:41:33Z"),
        pushedAt           = Instant.parse("2019-04-04T11:41:33Z"),
        isPrivate          = false,
        language           = Some("l2"),
        isArchived         = true,
        defaultBranch      = "b2",
        branchProtection   = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReview = true, requiresCommitSignatures = true)),
        repositoryYamlText = None,
        repoTypeHeuristics = dummyRepoTypeHeuristics
      ),
      GhRepository(
        name               = "n3",
        description        = None,
        htmlUrl            = "url3",
        fork               = true,
        createdDate        = Instant.parse("2019-04-05T11:41:33Z"),
        pushedAt           = Instant.parse("2019-04-06T11:41:33Z"),
        isPrivate          = true,
        language           = None,
        isArchived         = false,
        defaultBranch      = "b3",
        branchProtection   = None,
        repositoryYamlText = None,
        repoTypeHeuristics = dummyRepoTypeHeuristics
      )
    )

  "GithubConnector.getReposForTeam" should {
    "return repos" in {
      val team =
        GhTeam(name = "A Team", createdAt = createdAt)

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
        GhTeam(name = "A Team", createdAt = createdAt)

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

  "GithubConnector.getRepo" should {
    "return the repo" in {
      val query =
        getRepoQuery
          .withVariable("repo", JsString("n1"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
          .willReturn(aResponse().withBody(singleRepoJson))
      )

      connector.getRepo("n1").futureValue shouldBe Some(repo1)

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
      )
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
                """
                  {
                    "resources": {
                      "core": {
                        "limit": 100,
                        "used": 0,
                        "remaining": 100,
                        "reset": 1644407813
                      },
                      "search": {
                        "limit": 30,
                        "used": 0,
                        "remaining": 30,
                        "reset": 1644404273
                      },
                      "graphql": {
                        "limit": 200,
                        "used": 0,
                        "remaining": 200,
                        "reset": 1644407813
                      },
                      "integration_manifest": {
                        "limit": 5000,
                        "used": 0,
                        "remaining": 5000,
                        "reset": 1644407813
                      },
                      "source_import": {
                        "limit": 100,
                        "used": 0,
                        "remaining": 100,
                        "reset": 1644404273
                      },
                      "code_scanning_upload": {
                        "limit": 500,
                        "used": 0,
                        "remaining": 500,
                        "reset": 1644407813
                      },
                      "actions_runner_registration": {
                        "limit": 10000,
                        "used": 0,
                        "remaining": 10000,
                        "reset": 1644407813
                      },
                      "scim": {
                        "limit": 15000,
                        "used": 0,
                        "remaining": 15000,
                        "reset": 1644407813
                      }
                    },
                    "rate": {
                      "limit": 5000,
                      "used": 0,
                      "remaining": 5000,
                      "reset": 1644407813
                    }
                  }
                """
              )
          )
      )

      import RateLimitMetrics.Resource._

      connector.getRateLimitMetrics(token, Core).futureValue shouldBe RateLimitMetrics(
        limit     = 100,
        remaining = 100,
        reset     = 1644407813
      )

      connector.getRateLimitMetrics(token, GraphQl).futureValue shouldBe RateLimitMetrics(
        limit     = 200,
        remaining = 200,
        reset     = 1644407813
      )

      wireMockServer.verify(
        2,
        getRequestedFor(urlPathEqualTo("/rate_limit"))
          .withHeader("Authorization", equalTo(s"token $token"))
      )
    }
  }
}
