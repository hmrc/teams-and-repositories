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
import org.scalatest.{WordSpec, BeforeAndAfterEach}

trait GithubWireMockSpec extends WordSpec with BeforeAndAfterEach {

  val testHost = "localhost"
  val port = 7654

  def githubApiClient: GithubV3ApiClient with GithubEndpoints with GithubCredentialsProvider

  trait TestEndpoints extends GithubEndpoints {
    override def rootUrl: String = s"http://$testHost:$port"
    override def reposForTeamEndpoint(teamId: Long): String = s"/$teamId/repos"
    override def teamsForOrganisationEndpoint(organisation: String): String = s"/$organisation/teams"
    override def organisationsEndpoint: String = "/orgs"
  }

  trait TestCredentials extends GithubCredentialsProvider {
    val cred = GithubCredentials(s"http://$testHost:$port", "", "")
  }

  val wireMockServer = new WireMockServer(port)

  override def beforeEach() {
    wireMockServer.start()
    WireMock.configureFor(testHost, port)
  }

  override def afterEach() {
    wireMockServer.stop()
  }

  def githubReturns(organisations: Map[GhOrganization, Map[GhTeam, List[GhRepository]]]): Unit = {
    stubOrganisations(organisations.keys.toList)

    organisations.foreach { case (org, teams) =>
      stubTeamsFor(org, teams.keys.toList)
      teams.foreach { case (team, repos) =>
        stubReposFor(org, team, repos)
      }
    }
  }

  def stubReposFor(organisation: GhOrganization, team: GhTeam, repos: List[GhRepository]) {

    val reposJson = repos.map { repo =>
    s"""{
          |   "id": ${repo.id},
          |   "name": "${repo.name}",
          |   "full_name": "${organisation.login}/{$repo.name}",
          |   "owner": {
          |     "login": "${organisation.login}",
          | "id": ${organisation.id},
          | "avatar_url": "${githubApiClient.rootUrl}/avatars/u/${organisation.id}?",
          | "gravatar_id": "",
          | "url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}",
          | "html_url": "${githubApiClient.rootUrl}/${organisation.login}",
          | "followers_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/followers",
          | "following_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/following{/other_user}",
          | "gists_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/gists{/gist_id}",
          | "starred_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/starred{/owner}{/repo}",
          | "subscriptions_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/subscriptions",
          | "organizations_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/orgs",
          | "repos_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/repos",
          | "events_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/events{/privacy}",
          | "received_events_url": "${githubApiClient.rootUrl}/api/v3/users/${organisation.login}/received_events",
   | "type": "Organization",
   | "site_admin": false
   |   },
   |   "private": false,
   |   "html_url": "${repo.html_url}",
  |   "description": "${repo.name}",
|   "fork": false,
|   "url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}",
   |   "forks_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/forks",
|   "keys_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/keys{/key_id}",
|   "collaborators_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/collaborators{/collaborator}",
 |   "teams_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/teams",
  |   "hooks_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/hooks",
   |   "issue_events_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/issues/events{/number}",
   |   "events_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/events",
 |   "assignees_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/assignees{/user}",
  |   "branches_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/branches{/branch}",
  |   "tags_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/tags",
  |   "blobs_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/git/blobs{/sha}",
   |   "git_tags_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/git/tags{/sha}",
   |   "git_refs_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/git/refs{/sha}",
   |   "trees_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/git/trees{/sha}",
|   "statuses_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/statuses/{sha}",
|   "languages_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/languages",
 |   "stargazers_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/stargazers",
   |   "contributors_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/contributors",
   |   "subscribers_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/subscribers",
  |   "subscription_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/subscription",
  |   "commits_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/commits{/sha}",
 |   "git_commits_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/git/commits{/sha}",
|   "comments_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/comments{/number}",
|   "issue_comment_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/issues/comments{/number}",
 |   "contents_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/contents/{+path}",
 |   "compare_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/compare/{base}...{head}",
|   "merges_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/merges",
  |   "archive_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/{archive_format}{/ref}",
 |   "downloads_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/downloads",
  |   "issues_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/issues{/number}",
|   "pulls_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/pulls{/number}",
 |   "milestones_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/milestones{/number}",
   |   "notifications_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/notifications{?since,all,participating}",
|   "labels_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/labels{/name}",
  |   "releases_url": "${githubApiClient.rootUrl}/api/v3/repos/${organisation.login}/${repo.name}/releases{/id}",
  |   "created_at": "2015-01-06T13:55:49Z",
  |   "updated_at": "2015-01-28T14:29:46Z",
  |   "pushed_at": "2015-01-28T14:29:46Z",
  |   "git_url": "git://${githubApiClient.rootUrl}/${organisation.login}/${repo.name}.git",
  |   "ssh_url": "git@${githubApiClient.rootUrl}:${organisation.login}/${repo.name}.git",
|   "clone_url": "${githubApiClient.rootUrl}/${organisation.login}/${repo.name}.git",
|   "svn_url": "${githubApiClient.rootUrl}/${organisation.login}/${repo.name}",
  |   "homepage": null,
  |   "size": 836,
  |   "stargazers_count": 0,
  |   "watchers_count": 0,
  |   "language": "JavaScript",
  |   "has_issues": true,
  |   "has_downloads": true,
  |   "has_wiki": false,
  |   "has_pages": false,
  |   "forks_count": 0,
  |   "mirror_url": null,
  |   "open_issues_count": 0,
  |   "forks": 0,
  |   "open_issues": 0,
  |   "watchers": 0,
  |   "default_branch": "master",
  |   "permissions": {
  | "admin": true,
  | "push": true,
  | "pull": true
  |   }
  | }""".stripMargin
}.mkString(",")

    stubFor(
      get(urlPathEqualTo(s"${githubApiClient.reposForTeamEndpoint(team.id)}")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"[ $reposJson ]")))
  }

  def stubTeamsFor(organisation: GhOrganization, teams: List[GhTeam]) {

    val teamsJson = teams.map { team =>
      s"""{
         |   "name": "${team.name}",
         |   "id": ${team.id},
         |   "slug": "${team.name}",
         |   "description": "${team.name}",
         |   "permission": "admin",
         |   "url": "${githubApiClient.rootUrl}/api/v3/teams/${team.id}",
         |   "members_url": "${githubApiClient.rootUrl}/api/v3/teams/${team.id}/members{/member}",
         |   "repositories_url": "${githubApiClient.rootUrl}/api/v3/teams/${team.id}/repos",
         |   "privacy": "secret"
         | }""".stripMargin
    }.mkString(",")

    val url = s"${githubApiClient.teamsForOrganisationEndpoint(organisation.login)}"

    stubFor(
      get(urlPathEqualTo(url)).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"[ $teamsJson ]")))
  }

  def stubOrganisations(entries: List[GhOrganization]) {

    val organisationsJson = entries.map { entry =>
      s"""{
         |   "login": "${entry.login}",
         |   "id": ${entry.id},
         |   "url": "${githubApiClient.rootUrl}/api/v3/orgs/${entry.login}",
         |   "repos_url": "${githubApiClient.rootUrl}/api/v3/orgs/${entry.login}/repos",
         |   "events_url": "${githubApiClient.rootUrl}/api/v3/orgs/${entry.login}/events",
         |   "members_url": "${githubApiClient.rootUrl}/api/v3/orgs/${entry.login}/members{/member}",
         |   "public_members_url": "${githubApiClient.rootUrl}/api/v3/orgs/${entry.login}/public_members{/member}",
         |   "avatar_url": "${githubApiClient.rootUrl}/avatars/u/${entry.id}?",
         |   "description": ""
         | }""".stripMargin
    }.mkString(",")

    stubFor(
      get(urlPathEqualTo(s"${githubApiClient.organisationsEndpoint}")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"[ $organisationsJson ]")))
  }
}
