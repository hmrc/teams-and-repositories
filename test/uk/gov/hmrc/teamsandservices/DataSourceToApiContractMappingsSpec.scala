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
import uk.gov.hmrc.teamsandservices.DataSourceToApiContractMappings._

class DataSourceToApiContractMappingsSpec extends WordSpec with Matchers with OptionValues {

  val urlTemplates = UrlTemplates(
    ciOpen = Seq(UrlTemplate(
      name = "open",
      template = "http://open/$name"
    )),
    ciClosed = Seq(UrlTemplate(
      name = "closed",
      template = "http://closed/$name"
    ))
  )

  "Mapping between repositories and services" should {

    "create links for a closed service" in {

      val repos = Seq(Repository(
        "a-frontend",
        "https://not-open-github/org/a-frontend",
        isInternal = true,
        deployable = true))

      repos.asService(Seq("teamName"), urlTemplates).get shouldBe Service(
        "a-frontend",
        Seq("teamName"),
        Seq(Link("github", "https://not-open-github/org/a-frontend")),
        List(Link("closed", "http://closed/a-frontend"))
      )
    }

    "create links for a open service" in {

      val repo = Seq(Repository(
        "a-frontend",
        "https://github.com/org/a-frontend",
        deployable = true))

      repo.asService(Seq("teamName"), urlTemplates).value shouldBe Service(
        "a-frontend",
        Seq("teamName"),
        Seq(Link("github-open", "https://github.com/org/a-frontend")),
        List(Link("open", "http://open/a-frontend"))
      )
    }

    "create github links for both open and internal services if both are present, but only open ci links" in {

      val internalRepo = Repository(
        "a-frontend",
        "https://not-open-github/org/a-frontend",
        isInternal = true,
        deployable = true)

      val openRepo = Repository(
        "a-frontend",
        "https://github.com/org/a-frontend",
        deployable = true)

      val repos = Seq(internalRepo, openRepo)
      repos.asService(Seq("teamName"), urlTemplates).value shouldBe Service(
        "a-frontend",
        Seq("teamName"),
        Seq(
          Link("github", "https://not-open-github/org/a-frontend"),
          Link("github-open", "https://github.com/org/a-frontend")),
        List(Link("open", "http://open/a-frontend"))
      )
    }
  }
}
