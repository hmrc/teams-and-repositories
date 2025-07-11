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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNull, JsString}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.teamsandrepositories.connector.GhRepository.RepoTypeHeuristics
import uk.gov.hmrc.teamsandrepositories.connector.GithubConnector.{getOpenPrsForRepoQuery, getOpenPrsQuery, getRepoQuery, getReposForTeamQuery, getReposQuery, getTeamsQuery}
import uk.gov.hmrc.teamsandrepositories.model.OpenPullRequest

import java.time.Instant

class GithubConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with OptionValues
     with WireMockSupport
     with GuiceOneAppPerSuite:

  val token = "token"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "github.open.api.key"      -> token,
        "github.open.api.url"      -> wireMockUrl,
        "github.open.api.rawurl"   -> s"$wireMockUrl/raw",
        "github.excluded.users"    -> List("excluded@email.com"),
        "ratemetrics.githubtokens" -> List(),
        "metrics.jvm"              -> false,
        "jenkins.username" -> "",
        "jenkins.token" -> "",
        "jenkins.url" -> ""
      )
      .build()

  private val connector: GithubConnector =
    app.injector.instanceOf[GithubConnector]

  given HeaderCarrier = HeaderCarrier()

  val createdAt: Instant =
    Instant.parse("2019-03-01T12:00:00Z")

  "GithubConnector.getTeams" should:
    "return teams" in:
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

  val reposJson1 =
    """[
       {
         "databaseId": 1,
         "name": "n1",
         "description": "d1",
         "url": "url1",
         "isFork": false,
         "createdAt": "2019-01-01T11:41:33Z",
         "lastFiveCommits": {
           "target": {
             "history": {
               "nodes": [
                 {
                   "author": {
                     "email": "excluded@email.com"
                   },
                   "committedDate": "2019-05-02T11:41:33Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-04-02T11:41:33Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-09T07:23:21Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-08T21:25:38Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-08T16:50:46Z"
                 }
               ]
             }
           }
         },
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
             "requiresCommitSignatures": true,
             "requiredStatusCheckContexts": []
           }
         }
       },
       {
         "databaseId": 2,
         "name": "n2",
         "description": "d2",
         "url": "url2",
         "isFork": false,
         "createdAt": "2019-01-03T11:41:33Z",
         "lastFiveCommits": {
           "target": {
             "history": {
               "nodes": [
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-04-04T11:41:33Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-09T07:23:57Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-09T07:23:21Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-08T21:25:38Z"
                 },
                 {
                   "author": {
                     "email": "user@email.com"
                   },
                   "committedDate": "2019-02-08T16:50:46Z"
                 }
               ]
             }
           }
         },
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
             "requiresCommitSignatures": true,
             "requiredStatusCheckContexts": []
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
           "createdAt": "2019-01-05T11:41:33Z",
           "lastFiveCommits": {
             "target": {
               "history": {
                 "nodes": [
                   {
                     "author": {
                       "email": "user@email.com"
                     },
                     "committedDate": "2019-04-06T11:41:33Z"
                   },
                   {
                     "author": {
                       "email": "user@email.com"
                     },
                     "committedDate": "2019-02-09T07:23:57Z"
                   },
                   {
                     "author": {
                       "email": "user@email.com"
                     },
                     "committedDate": "2019-02-09T07:23:21Z"
                   },
                   {
                     "author": {
                       "email": "user@email.com"
                     },
                     "committedDate": "2019-02-08T21:25:38Z"
                   },
                   {
                     "author": {
                       "email": "user@email.com"
                     },
                     "committedDate": "2019-02-08T16:50:46Z"
                   }
                 ]
               }
             }
           },
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
              "createdAt": "2019-01-01T11:41:33Z",
              "lastFiveCommits": {
                "target": {
                  "history": {
                    "nodes": [
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-04-02T11:41:33Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-09T07:23:57Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-09T07:23:21Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-08T21:25:38Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-08T16:50:46Z"
                      }
                    ]
                  }
                }
              },
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
                  "requiresCommitSignatures": true,
                  "requiredStatusCheckContexts": []
                }
              }
            }
          }
        }
      }
     """

  val openPrsJson =
    """
      {
        "data": {
          "organization": {
            "repositories": {
              "nodes": [
                {
                  "name": "example-repo1",
                  "pullRequests": {
                    "nodes": []
                  }
                },
                {
                  "name": "example-repo2",
                  "pullRequests": {
                    "nodes": [
                      {
                        "title": "Some PR Title",
                        "url": "https://github.com/example-repo2/pull/1",
                        "author": {
                          "login": "username1"
                        },
                        "createdAt": "2020-03-13T11:18:06Z"
                      }
                    ]
                  }
                },
                {
                  "name": "example-repo3",
                  "pullRequests": {
                    "nodes": [
                      {
                        "title": "Some PR Title",
                        "url": "https://github.com/example-repo3/pull/1",
                        "author": {
                          "login": "username2"
                        },
                        "createdAt": "2020-03-13T11:18:06Z"
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
      }
    """

  val openPrsForRepoJson =
    s"""
        {
         "data": {
           "organization": {
             "repository": {
               "pullRequests": {
                 "nodes": [
                   {
                     "title": "PR Title 1",
                     "url": "https://github.com/example-repo/pull/1",
                     "author": {
                       "login": "author1"
                     },
                     "createdAt": "2020-03-13T11:18:06Z"
                   },
                   {
                     "title": "PR Title 2",
                     "url": "https://github.com/example-repo/pull/2",
                     "author": {
                       "login": "author2"
                     },
                     "createdAt": "2020-03-12T11:18:06Z"
                   },
                   {
                     "title": "PR Title 3",
                     "url": "https://github.com/example-repo/pull/3",
                     "author": {
                       "login": "author3"
                     },
                     "createdAt": "2020-03-11T11:18:06Z"
                   }
                 ]
               }
             }
           }
         }
       }
       """

  def heuristicsJson(repoName: String): String =
    s"""
      {
        "data": {
          "organization": {
            "repository": {
              "databaseId": 1,
              "name": "$repoName",
              "description": "d1",
              "url": "url1",
              "isFork": false,
              "createdAt": "2019-01-01T11:41:33Z",
              "lastFiveCommits": {
                "target": {
                  "history": {
                    "nodes": [
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-04-02T11:41:33Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-09T07:23:57Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-09T07:23:21Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-08T21:25:38Z"
                      },
                      {
                        "author": {
                          "email": "user@email.com"
                        },
                        "committedDate": "2019-02-08T16:50:46Z"
                      }
                    ]
                  }
                }
              },
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
                  "requiresCommitSignatures": true,
                  "requiredStatusCheckContexts": []
                }
              }
            }
          }
        }
      }
     """

  val dummyRepoTypeHeuristics: RepoTypeHeuristics =
    RepoTypeHeuristics(
      prototypeInName     = false,
      testsInName         = false,
      hasApplicationConf  = false,
      hasDeployProperties = false,
      hasProcfile         = false,
      hasSrcMainScala     = false,
      hasSrcMainJava      = false,
      hasPomXml           = false,
      hasTags             = false
    )

  val repo1: GhRepository =
    GhRepository(
      name               = "n1",
      htmlUrl            = "url1",
      fork               = false,
      createdDate        = Instant.parse("2019-01-01T11:41:33Z"),
      pushedAt           = Instant.parse("2019-04-02T11:41:33Z"),
      isPrivate          = true,
      language           = Some("l1"),
      isArchived         = false,
      defaultBranch      = "b1",
      branchProtection   = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReview = true, requiresCommitSignatures = true)),
      repositoryYamlText = None,
      repoTypeHeuristics = dummyRepoTypeHeuristics
    )

  val repos: List[GhRepository] =
    List(
      repo1,
      GhRepository(
        name               = "n2",
        htmlUrl            = "url2",
        fork               = false,
        createdDate        = Instant.parse("2019-01-03T11:41:33Z"),
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
        htmlUrl            = "url3",
        fork               = true,
        createdDate        = Instant.parse("2019-01-05T11:41:33Z"),
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

  "GithubConnector.getOpenPrs" should :
    "return all open pull requests" in :
      val openPullRequests =
        List(
          OpenPullRequest(
            repoName  = "example-repo2",
            title     = "Some PR Title",
            url       = "https://github.com/example-repo2/pull/1",
            author    = "username1",
            createdAt = Instant.parse("2020-03-13T11:18:06Z")
          ),
          OpenPullRequest(
            repoName  = "example-repo3",
            title     = "Some PR Title",
            url       = "https://github.com/example-repo3/pull/1",
            author    = "username2",
            createdAt = Instant.parse("2020-03-13T11:18:06Z")
          )
        )

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getOpenPrsQuery.asJsonString))
          .willReturn(aResponse().withBody(openPrsJson))
      )

      connector.getOpenPrs().futureValue shouldBe openPullRequests

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getOpenPrsQuery.asJsonString))
      )

  "GithubConnector.getReposForTeam" should:
    "return repos" in:
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

    "return an empty list when a team does not exist" in:
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

  "GithubConnector.openPrsForRepo" should :
    "return all open pull requests for a given repository" in :
      val repoName = "example-repo"
      val openPullRequests =
        List(
          OpenPullRequest(
            repoName = repoName,
            title = "PR Title 1",
            url = "https://github.com/example-repo/pull/1",
            author = "author1",
            createdAt = Instant.parse("2020-03-13T11:18:06Z")
          ),
          OpenPullRequest(
            repoName = repoName,
            title = "PR Title 2",
            url = "https://github.com/example-repo/pull/2",
            author = "author2",
            createdAt = Instant.parse("2020-03-12T11:18:06Z")
          ),
          OpenPullRequest(
            repoName = repoName,
            title = "PR Title 3",
            url = "https://github.com/example-repo/pull/3",
            author = "author3",
            createdAt = Instant.parse("2020-03-11T11:18:06Z")
          )
        )

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getOpenPrsForRepoQuery.withVariable("repoName", JsString(repoName)).asJsonString))
          .willReturn(aResponse().withBody(openPrsForRepoJson))
      )

      connector.getOpenPrsForRepo(repoName).futureValue shouldBe openPullRequests

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(getOpenPrsForRepoQuery.withVariable("repoName", JsString(repoName)).asJsonString))
      )

    "return an empty list when no open pull requests exist" in :
      val repoName = "example-repo"

      val query =
        getOpenPrsForRepoQuery
          .withVariable("repoName", JsString(repoName))

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
                          "repository": {
                            "pullRequests": {
                              "pageInfo": {
                                "endCursor": null
                              },
                              "nodes": null
                            }
                          }
                        }
                      }
                    }
                  """
              )
          )
      )

      connector.getOpenPrsForRepo(repoName).futureValue shouldBe empty

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
      )

  "GithubConnector.getRepos" should:
    "return repos" in:
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

  "GithubConnector.getRepo" should:
    "return the repo" in:
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

  "RepoTypeHeuristics" should:
    "infer the repo Type to be a test, if the name ends in -test" in:
      val query =
        getRepoQuery
          .withVariable("repo", JsString("n1-test"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
          .willReturn(aResponse().withBody(heuristicsJson(repoName = "n1-test")))
      )

      connector.getRepo("n1-test").futureValue shouldBe Some(
        repo1.copy(
          name = repo1.name + "-test",
          repoTypeHeuristics = repo1.repoTypeHeuristics.copy(testsInName = true)
        )
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
      )

    "infer the repo Type to be a test, if the name ends in -tests" in:
      val query =
        getRepoQuery
          .withVariable("repo", JsString("n1-tests"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
          .willReturn(aResponse().withBody(heuristicsJson(repoName = "n1-tests")))
      )

      connector.getRepo("n1-tests").futureValue shouldBe Some(
        repo1.copy(
          name = repo1.name + "-tests",
          repoTypeHeuristics = repo1.repoTypeHeuristics.copy(testsInName = true)
        )
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
      )

    "fail to infer the repoType to be a test, if the name ends in a typo" in:
      val query =
        getRepoQuery
          .withVariable("repo", JsString("n1-testss"))

      stubFor(
        post(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
          .willReturn(aResponse().withBody(heuristicsJson(repoName = "n1-testss")))
      )

      connector.getRepo("n1-testss").futureValue shouldBe Some(
        repo1.copy(
          name = repo1.name + "-testss"
        )
      )

      wireMockServer.verify(
        postRequestedFor(urlPathEqualTo("/graphql"))
          .withRequestBody(equalToJson(query.asJsonString))
      )

  "GithubConnector.getRateLimitMetrics" should:
    "return rate limit metrics" in:
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
  
  "updateBranchProtectionRules" should:
    "error when non 2xx response" in:
      stubFor(
        put(urlPathEqualTo("/repos/hmrc/some-repo/branches/main/protection"))
          .willReturn(
            aResponse()
              .withStatus(422)
          )
      )

      val updatedRules = BranchProtectionRules(None, None, true, true, false, false, false, false, false, false, false, JsNull)

      val exception = intercept[RuntimeException] {
          connector.updateBranchProtectionRules("some-repo", "main", updatedRules).futureValue
      }

      exception.getMessage should include("failed with status: 422")
