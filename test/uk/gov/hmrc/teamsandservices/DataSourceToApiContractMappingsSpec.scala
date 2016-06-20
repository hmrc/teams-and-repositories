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

package uk.gov.hmrc.teamsandservices

import org.scalatest.{Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.teamsandservices.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.teamsandservices.TeamRepositoryWrapper._

class DataSourceToApiContractMappingsSpec extends WordSpec with Matchers with OptionValues {

  import TeamRepositoryWrapper._

  val urlTemplates = UrlTemplates(
    ciOpen = Seq(UrlTemplate(
      name = "open",
      template = "http://open/$name"
    )),
    ciClosed = Seq(UrlTemplate(
      name = "closed",
      template = "http://closed/$name"
    )),
    environments = Map(
      "env1" -> Seq(
        new UrlTemplate("kibana", "$name"),
        new UrlTemplate("grafana", "$name")),
      "env2" -> Seq(
        new UrlTemplate("kibana", "$name"))
    )
  )

  "Mapping between repositories and services" should {

    "create links for a closed service" in {

      val repos = Seq(Repository(
        "a-frontend",
        "https://not-open-github/org/a-frontend",
        isInternal = true,
        isDeployable = true))

      val service = repoGroupToService(repos, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe Seq(Link("GitHub Enterprise", "https://not-open-github/org/a-frontend"))
      service.ci shouldBe List(Link("closed", "http://closed/a-frontend"))
    }

    "create links for a open service" in {

      val repo = Seq(Repository(
        "a-frontend",
        "https://github.com/org/a-frontend",
        isDeployable = true))

      val service = repoGroupToService(repo, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe Seq(Link("GitHub.com", "https://github.com/org/a-frontend"))
      service.ci shouldBe List(Link("open", "http://open/a-frontend"))
    }

    "create links for each environment" in {
      val aFrontend = Repository(
        "a-frontend",
        "https://not-open-github/org/a-frontend",
        isDeployable = true)


      val repos = Seq(aFrontend)

      val service = repoGroupToService(repos, Seq("teamName"), urlTemplates)

      service.environments.size shouldBe 2
      service.environments.find(_.name == "env1").value shouldBe Environment("env1", Seq(Link("kibana","a-frontend"), Link("grafana","a-frontend")))
      service.environments.find(_.name == "env2").value shouldBe Environment("env2", Seq(Link("kibana","a-frontend")))
    }

    "create github links for both open and internal services if both are present, but only open ci links" in {

      val internalRepo = Repository(
        "a-frontend",
        "https://not-open-github/org/a-frontend",
        isInternal = true,
        isDeployable = true)

      val openRepo = Repository(
        "a-frontend",
        "https://github.com/org/a-frontend",
        isDeployable = true)

      val repos = Seq(internalRepo, openRepo)
      val service = repoGroupToService(repos, Seq("teamName"), urlTemplates)

      service.githubUrls shouldBe Seq(
        Link("GitHub Enterprise", "https://not-open-github/org/a-frontend"),
        Link("GitHub.com", "https://github.com/org/a-frontend"))

      service.ci shouldBe List(Link("open", "http://open/a-frontend"))
    }
  }
}
