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

import org.scalatest.{Matchers, OptionValues, WordSpec}
import play.api.libs.json.Json
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.teamsandrepositories.controller.model.{Environment, Link}
import uk.gov.hmrc.time.DateTimeUtils

import scala.collection.immutable.ListMap

class GitRepositorySpec extends WordSpec with Matchers with OptionValues {

  val now = DateTimeUtils.now.getMillis

  val urlTemplates = UrlTemplates(
    ciOpen = Seq(
      UrlTemplate(
        name        = "open1",
        displayName = "open 1",
        template    = "http://open/$name"
      )),
    ciClosed = Seq(
      UrlTemplate(
        name        = "closed1",
        displayName = "closed 1",
        template    = "http://closed/$name"
      )),
    environments = ListMap(
      "env1" -> Seq(new UrlTemplate("log1", "log 1", "$name"), new UrlTemplate("mon1", "mon 1", "$name")),
      "env2" -> Seq(new UrlTemplate("log1", "log 1", "$name"))
    )
  )

  def githubLink(url: String) = Link("github-com", "GitHub.com", url)

  "repoGroupToRepositoryDetails" should {

    "create links for open libraries" in {

      val repo =
        GitRepository(
          "a-library",
          "Some Description",
          "https://github.com/org/a-library",
          now,
          now,
          repoType = RepoType.Library,
          language = Some("Scala")
        )

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Library, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe List(githubLink("https://github.com/org/a-library"))
      service.ci         shouldBe List(Link("open1", "open 1", "http://open/a-library"))
    }

    "create links for open services" in {

      val repo =
        GitRepository(
          "a-frontend",
          "Some Description",
          "https://github.com/org/a-frontend",
          now,
          now,
          repoType = RepoType.Service,
          language = Some("Scala")
        )

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Service, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe List(githubLink("https://github.com/org/a-frontend"))
      service.ci         shouldBe List(Link("open1", "open 1", "http://open/a-frontend"))
    }

    "create links for private libraries" in {

      val repo =
        GitRepository(
          "a-library",
          "Some Description",
          "https://github.com/org/a-library",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Library,
          language  = Some("Scala")
        )

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Library, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe List(githubLink("https://github.com/org/a-library"))
      service.ci         shouldBe List(Link("closed1", "closed 1", "http://closed/a-library"))
    }

    "create links for private services" in {

      val repo =
        GitRepository(
          "a-frontend",
          "Some Description",
          "https://github.com/org/a-frontend",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Service,
          language  = Some("Scala")
        )

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Service, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe List(githubLink("https://github.com/org/a-frontend"))
      service.ci         shouldBe List(Link("closed1", "closed 1", "http://closed/a-frontend"))
    }

    "create links for each environment" in {
      val aFrontend =
        GitRepository(
          "a-frontend",
          "Some Description",
          "https://github.com/org/a-frontend",
          now,
          now,
          repoType = RepoType.Service,
          language = Some("Scala")
        )

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Service, aFrontend, Seq("teamName"), urlTemplates)

      service.environments.size shouldBe 2

      service.environments.find(_.name == "env1").value shouldBe Environment(
        "env1",
        List(Link("log1", "log 1", "a-frontend"), Link("mon1", "mon 1", "a-frontend")))
      service.environments.find(_.name == "env2").value shouldBe Environment(
        "env2",
        List(Link("log1", "log 1", "a-frontend")))
    }

    "do not create environment links for libraries" in {
      val aLibrary = GitRepository(
        "a-library",
        "Some Description",
        "https://not-open-github/org/a-library",
        now,
        now,
        repoType = RepoType.Library,
        language = Some("Scala"))

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Library, aLibrary, Seq("teamName"), urlTemplates)

      service.environments shouldBe Seq.empty
    }

    "create github and ci links for open services" in {
      val repo = GitRepository(
        "a-frontend",
        "Some Description",
        "https://github.com/org/a-frontend",
        now,
        now,
        repoType = RepoType.Service,
        language = Some("Scala"))

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Service, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe Seq(githubLink("https://github.com/org/a-frontend"))

      service.ci shouldBe List(Link("open1", "open 1", "http://open/a-frontend"))
    }

    "create github and ci links for open libraries" in {
      val repo = GitRepository(
        "a-library",
        "Some Description",
        "https://github.com/org/a-library",
        now,
        now,
        repoType = RepoType.Library,
        language = Some("Scala"))

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Library, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe Seq(githubLink("https://github.com/org/a-library"))

      service.ci shouldBe List(Link("open1", "open 1", "http://open/a-library"))
    }

    "just create github links if not Deployable or Library" in {
      val repo = GitRepository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo",
        now,
        now,
        repoType = RepoType.Other,
        language = Some("Scala"))

      val service = GitRepository.repoGroupToRepositoryDetails(RepoType.Other, repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe Seq(githubLink("https://github.com/org/a-repo"))

      service.ci           shouldBe Seq.empty
      service.environments shouldBe Seq.empty
    }

    "Should use available language if only one repo" in {
      val internalRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        now,
        now,
        repoType = RepoType.Service,
        language = Some("Scala"))

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Service, internalRepo, Seq("teamName"), urlTemplates)

      service.language shouldBe "Scala"
    }

    "Should take language if open and internal are the same" in {
      val internalRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        now,
        now,
        // isInternal = true,
        repoType = RepoType.Service,
        language = Some("Scala"))
      val openRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo",
        now,
        now,
        repoType = RepoType.Service,
        language = Some("Scala"))

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Service, internalRepo, Seq("teamName"), urlTemplates)

      service.language shouldBe "Scala"
    }

    "Should take non-empty language if open and internal both have values but one is empty" in {
      val internalRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        now,
        now,
        // isInternal = true,
        repoType = RepoType.Service,
        language = Some("Scala"))
      val openRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo",
        now,
        now,
        repoType = RepoType.Service,
        language = Some(""))

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Service, internalRepo, Seq("teamName"), urlTemplates)

      service.language shouldBe "Scala"
    }

    "Should take non-empty language if open and internal both have values but one is None" in {
      val internalRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        now,
        now,
        // isInternal = true,
        repoType = RepoType.Service,
        language = Some("Scala"))
      val openRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo",
        now,
        now,
        repoType = RepoType.Service,
        language = None)

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Service, internalRepo, Seq("teamName"), urlTemplates)

      service.language shouldBe "Scala"
    }

    "Should take ci-open service name both are present and different" in {
      val internalRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        now,
        now,
        // isInternal = true,
        repoType = RepoType.Service,
        language = Some("Python"))
      val openRepo = GitRepository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo",
        now,
        now,
        repoType = RepoType.Service,
        language = Some("Scala"))

      val service =
        GitRepository.repoGroupToRepositoryDetails(RepoType.Service, openRepo, Seq("teamName"), urlTemplates)

      service.language shouldBe "Scala"
    }

  }

  "GitRepository" should {
    "read an object without the isPrivate field" in {

      GitRepository.gitRepositoryFormats
        .reads(
          Json.parse("""
          |{"name":"a-repo",
          |"description":"Some Description",
          |"url":"https://not-open-github/org/a-repo",
          |"createdDate":1499417808270,
          |"lastActiveDate":1499417808270,
          |"// isInternal":true,
          |"repoType":"Other",
          |"updateDate":123,
          |"language":"Scala"}""".stripMargin)
        )
        .get shouldBe GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        1499417808270L,
        1499417808270L,
        // isInternal = true,
        repoType = RepoType.Other,
        language = Some("Scala")
      )

    }
  }
}
