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

package uk.gov.hmrc.teamsandrepositories

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.teamsandrepositories.controller.model.{Environment, Link, RepositoryDetails}

import scala.collection.immutable.ListMap

class GitRepositorySpec extends AnyWordSpec with Matchers with OptionValues {

  val now = System.currentTimeMillis()

  val urlTemplates = UrlTemplates(
    environments = ListMap(
      "env1" -> Seq(new UrlTemplate("log1", "log 1", "$name"), new UrlTemplate("mon1", "mon 1", "$name")),
      "env2" -> Seq(new UrlTemplate("log1", "log 1", "$name"))
    )
  )

  def githubLink(url: String) = Link("github-com", "GitHub.com", url)

  "repoGroupToRepositoryDetails" should {
    "create links for libraries" in {
      val repo =
        GitRepository(
          "a-library",
          "Some Description",
          "https://github.com/org/a-library",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Library,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq("a-team"), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-library")
      repoDetails.ci        shouldBe List(Link("Build", "Build", "https://build.tax.service.gov.uk/job/a-team/job/a-library"))
    }

    "create ci links for libraries belonging to multiple teams" in {
      val repo =
        GitRepository(
          "a-library",
          "Some Description",
          "https://github.com/org/a-library",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Library,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq("a-team", "another-team"), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-library")
      repoDetails.ci shouldBe List(
        Link("a-team Build", "a-team Build", "https://build.tax.service.gov.uk/job/a-team/job/a-library"),
        Link(
          "another-team Build",
          "another-team Build",
          "https://build.tax.service.gov.uk/job/another-team/job/a-library")
      )
    }

    "not create ci links for libraries without a team" in {
      val repo =
        GitRepository(
          "a-library",
          "Some Description",
          "https://github.com/org/a-library",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Library,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq(), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-library")
      repoDetails.ci        shouldBe List()
    }

    "url encode ci links with spaces correctly" in {
      val repo =
        GitRepository(
          "a-library",
          "Some Description",
          "https://github.com/org/a-library",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Library,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq("a team"), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-library")
      repoDetails.ci shouldBe List(
        Link("Build", "Build", "https://build.tax.service.gov.uk/job/a%20team/job/a-library"))
    }

    "create links for services" in {
      val repo =
        GitRepository(
          "a-service",
          "Some Description",
          "https://github.com/org/a-service",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Service,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq("a-team"), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-service")
      repoDetails.ci        shouldBe List(Link("Build", "Build", "https://build.tax.service.gov.uk/job/a-team/job/a-service"))
    }

    "create ci links for services belonging to multiple teams" in {
      val repo =
        GitRepository(
          "a-service",
          "Some Description",
          "https://github.com/org/a-service",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Service,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq("a-team", "another-team"), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-service")
      repoDetails.ci shouldBe List(
        Link("a-team Build", "a-team Build", "https://build.tax.service.gov.uk/job/a-team/job/a-service"),
        Link(
          "another-team Build",
          "another-team Build",
          "https://build.tax.service.gov.uk/job/another-team/job/a-service")
      )
    }

    "not create ci links for services without a team" in {
      val repo =
        GitRepository(
          "a-service",
          "Some Description",
          "https://github.com/org/a-service",
          now,
          now,
          isPrivate = true,
          repoType  = RepoType.Service,
          language  = Some("Scala"),
          archived  = false
        )

      val repoDetails = RepositoryDetails.create(repo, Seq(), urlTemplates)

      repoDetails.githubUrl shouldBe githubLink("https://github.com/org/a-service")
      repoDetails.ci        shouldBe List()
    }

    "not create ci links for prototype repositories" in {
      val aLibrary =
        GitRepository(
          "a-prototype",
          "Some Description",
          "https://not-open-github/org/a-library",
          now,
          now,
          repoType = RepoType.Prototype,
          language = Some("Scala"),
          archived = false
        )

      val repoDetails = RepositoryDetails.create(aLibrary, Seq("teamName"), urlTemplates)

      repoDetails.ci shouldBe Seq.empty
    }

    "not create ci links for other type of repositories" in {
      val aLibrary =
        GitRepository(
          "a-other",
          "Some Description",
          "https://not-open-github/org/a-library",
          now,
          now,
          repoType = RepoType.Other,
          language = Some("Scala"),
          archived = false
        )

      val repoDetails = RepositoryDetails.create(aLibrary, Seq("teamName"), urlTemplates)

      repoDetails.ci shouldBe Seq.empty
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
          language = Some("Scala"),
          archived = false
        )

      val repoDetails = RepositoryDetails.create(aFrontend, Seq("teamName"), urlTemplates)

      repoDetails.environments.size shouldBe 2

      repoDetails.environments.find(_.name == "env1").value shouldBe Environment(
        "env1",
        List(Link("log1", "log 1", "a-frontend"), Link("mon1", "mon 1", "a-frontend")))
      repoDetails.environments.find(_.name == "env2").value shouldBe Environment(
        "env2",
        List(Link("log1", "log 1", "a-frontend")))
    }

    "not create environment links for libraries" in {
      val aLibrary =
        GitRepository(
          "a-library",
          "Some Description",
          "https://not-open-github/org/a-library",
          now,
          now,
          repoType = RepoType.Library,
          language = Some("Scala"),
          archived = false
        )

      val repoDetails = RepositoryDetails.create(aLibrary, Seq("teamName"), urlTemplates)

      repoDetails.environments shouldBe Seq.empty
    }

    "just create github links if not Deployable or Library" in {
      val repo = GitRepository(
        "a-repo",
        "Some Description",
        "https://github.com/org/a-repo",
        now,
        now,
        repoType = RepoType.Other,
        language = Some("Scala"),
        archived = false)

      val repoDetails = RepositoryDetails.create(repo, Seq("teamName"), urlTemplates)

      repoDetails.githubUrl    shouldBe githubLink("https://github.com/org/a-repo")
      repoDetails.ci           shouldBe Seq.empty
      repoDetails.environments shouldBe Seq.empty
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
          |"repoType":"Other",
          |"updateDate":123,
          |"language":"Scala",
          |"archived": true}""".stripMargin)
        )
        .get shouldBe GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        1499417808270L,
        1499417808270L,
        // isInternal = true,
        repoType = RepoType.Other,
        language = Some("Scala"),
        archived = true
      )

    }

    "read an object without the archived field" in {

      GitRepository.gitRepositoryFormats
        .reads(
          Json.parse("""
                       |{"name":"a-repo",
                       |"description":"Some Description",
                       |"url":"https://not-open-github/org/a-repo",
                       |"createdDate":1499417808270,
                       |"lastActiveDate":1499417808270,
                       |"repoType":"Other",
                       |"updateDate":123,
                       |"language":"Scala"
                       |}""".stripMargin)
        )
        .get shouldBe GitRepository(
        "a-repo",
        "Some Description",
        "https://not-open-github/org/a-repo",
        1499417808270L,
        1499417808270L,
        // isInternal = true,
        repoType = RepoType.Other,
        language = Some("Scala"),
        archived = false
      )

    }
  }
}
