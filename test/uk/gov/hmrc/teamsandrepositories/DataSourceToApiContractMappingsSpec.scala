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

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.util.Date

import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.teamsandrepositories.TeamRepositoryWrapper._

class DataSourceToApiContractMappingsSpec extends WordSpec with Matchers with OptionValues {

  val now = new Date().getTime

  import TeamRepositoryWrapper._

  val urlTemplates = UrlTemplates(
    ciOpen = Seq(UrlTemplate(
      name = "open1",
      displayName = "open 1",
      template = "http://open/$name"
    )),
    ciClosed = Seq(UrlTemplate(
      name = "closed1",
      displayName = "closed 1",
      template = "http://closed/$name"
    )),
    environments = Map(
      "env1" -> Seq(
        new UrlTemplate("log1", "log 1", "$name"),
        new UrlTemplate("mon1", "mon 1", "$name")),
      "env2" -> Seq(
        new UrlTemplate("log1", "log 1", "$name"))
    )
  )

  def enterpriseGithubLink(url: String) = Link("github-enterprise", "Github Enterprise", url)

  def openGithubLink(url: String) = Link("github-com", "GitHub.com", url)

  "Mapping between repositories and services" should {

    "create links for a closed service" in {

      val repos = Seq(Repository(
        "a-frontend",
        "Some Description",
        "https://not-open-github/org/a-frontend",
        now,
        now,
        isInternal = true,
        repoType = RepoType.Deployable))

      val service = repoGroupToRepositoryDetails(RepoType.Deployable, repos, Seq("teamName"), urlTemplates)

      service.get.githubUrls shouldBe List(enterpriseGithubLink("https://not-open-github/org/a-frontend"))
      service.get.ci shouldBe List(Link("closed1", "closed 1", "http://closed/a-frontend"))
    }

    "create links for a closed libraries" in {

      val repos = Seq(Repository(
        "a-library",
        "Some Description",
        "https://not-open-github/org/a-library",
        now,
        now,
        isInternal = true,
        repoType = RepoType.Library))

      val service = repoGroupToRepositoryDetails(RepoType.Library, repos, Seq("teamName"), urlTemplates)

      service.get.githubUrls shouldBe List(enterpriseGithubLink("https://not-open-github/org/a-library"))
      service.get.ci shouldBe List(Link("closed1", "closed 1", "http://closed/a-library"))
    }

    "create links for a open Libraries" in {

      val repo = Seq(Repository(
        "a-library",
        "Some Description",
        "https://github.com/org/a-library", now, now,
        repoType = RepoType.Library))

      val service = repoGroupToRepositoryDetails(RepoType.Library, repo, Seq("teamName"), urlTemplates)


      service.get.githubUrls shouldBe List(openGithubLink("https://github.com/org/a-library"))
      service.get.ci shouldBe List(Link("open1", "open 1", "http://open/a-library"))
    }

    "create links for a open service" in {

      val repo = Seq(Repository(
        "a-frontend",
        "Some Description",
        "https://github.com/org/a-frontend", now, now,
        repoType = RepoType.Deployable))

      val service = repoGroupToRepositoryDetails(RepoType.Deployable, repo, Seq("teamName"), urlTemplates)


      service.get.githubUrls shouldBe List(openGithubLink("https://github.com/org/a-frontend"))
      service.get.ci shouldBe List(Link("open1", "open 1", "http://open/a-frontend"))
    }


    "create links for each environment" in {
      val aFrontend = Repository(
        "a-frontend",
        "Some Description",
        "https://not-open-github/org/a-frontend", now, now,
        repoType = RepoType.Deployable)


      val repos = Seq(aFrontend)

      val service = repoGroupToRepositoryDetails(RepoType.Deployable, repos, Seq("teamName"), urlTemplates)

      service.get.environments.size shouldBe 2

      service.get.environments.find(_.name == "env1").value shouldBe Environment("env1", List(Link("log1", "log 1", "a-frontend"), Link("mon1", "mon 1", "a-frontend")))
      service.get.environments.find(_.name == "env2").value shouldBe Environment("env2", List(Link("log1", "log 1", "a-frontend")))
    }

    "do not create environment links for libraries" in {
      val aLibrary = Repository(
        "a-library",
        "Some Description",
        "https://not-open-github/org/a-library", now, now,
        repoType = RepoType.Library)


      val repos = Seq(aLibrary)

      val service = repoGroupToRepositoryDetails(RepoType.Library, repos, Seq("teamName"), urlTemplates)

      service.get.environments shouldBe Seq.empty

    }


    "create github links for both open and internal services if both are present, but only open ci links" in {

      val internalRepo = Repository(
        "a-frontend",
        "Some Description",
        "https://not-open-github/org/a-frontend", now, now,
        isInternal = true,
        repoType = RepoType.Deployable)

      val openRepo = Repository(
        "a-frontend",
        "Some Description",
        "https://github.com/org/a-frontend", now, now,
        repoType = RepoType.Deployable)

      val repos = Seq(internalRepo, openRepo)
      val service = repoGroupToRepositoryDetails(RepoType.Deployable, repos, Seq("teamName"), urlTemplates)

      service.get.githubUrls shouldBe Seq(
        enterpriseGithubLink("https://not-open-github/org/a-frontend"),
        openGithubLink("https://github.com/org/a-frontend"))

      service.get.ci shouldBe List(Link("open1", "open 1", "http://open/a-frontend"))
    }

    "create github links for both open and internal Libraries if both are present, but only ci-open links" in {

      val internalRepo = Repository(
        "a-library",
        "Some Description",
        "https://not-open-github/org/a-library", now, now,
        isInternal = true,
        repoType = RepoType.Library)

      val openRepo = Repository(
        "a-library",
        "Some Description",
        "https://github.com/org/a-library", now, now,
        repoType = RepoType.Library)

      val repos = Seq(internalRepo, openRepo)
      val service = repoGroupToRepositoryDetails(RepoType.Library, repos, Seq("teamName"), urlTemplates)

      service.get.githubUrls shouldBe Seq(
        enterpriseGithubLink("https://not-open-github/org/a-library"),
        openGithubLink("https://github.com/org/a-library"))

      service.get.ci shouldBe List(Link("open1", "open 1", "http://open/a-library"))
    }


    "just create github links if not Deployable or Library" in {

      val internalRepo = Repository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo", now, now,
        isInternal = true,
        repoType = RepoType.Other)

      val openRepo = Repository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo", now, now,
        repoType = RepoType.Other)

      val repos = Seq(internalRepo, openRepo)
      val service = repoGroupToRepositoryDetails(RepoType.Other, repos, Seq("teamName"), urlTemplates)

      service.get.githubUrls shouldBe Seq(
        enterpriseGithubLink("https://not-open-github/org/a-repo"),
        openGithubLink("https://github.com/org/a-repo"))

      service.get.ci shouldBe Seq.empty
      service.get.environments shouldBe Seq.empty
    }

  }
}
