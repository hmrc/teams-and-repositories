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

package uk.gov.hmrc.catalogue

import org.scalatest.{BeforeAndAfterEach, Matchers, OptionValues, WordSpec}
import uk.gov.hmrc.catalogue.config.{UrlTemplate, UrlTemplates}
import uk.gov.hmrc.catalogue.teams.ViewModels.{Link, Service, Repository}

class TeamServicesSpec extends WordSpec with Matchers with BeforeAndAfterEach with OptionValues {

  implicit val urlTemplates = UrlTemplates(
    ciOpen = Seq(UrlTemplate(
      name = "open",
      template = "http://open/$name"
    )),
    ciClosed = Seq(UrlTemplate(
      name = "closed",
      template = "http://closed/$name"
    ))
  )

  "Services" should {
    "create links for a closed service" in {

      val repo = Repository(
        "a-frontend",
        "https://not-open-github/org/a-frontend",
        isMicroservice = true)

      Service.fromRepository(repo).value shouldBe Service(
        "a-frontend",
        Link("github", "https://not-open-github/org/a-frontend"),
        List(
          Link("closed", "http://closed/a-frontend")
        )
      )
    }
    "create links for a open service" in {

      val repo = Repository(
        "a-frontend",
        "https://github.com/org/a-frontend",
        isMicroservice = true)

      Service.fromRepository(repo).value shouldBe Service(
        "a-frontend",
        Link("github", "https://github.com/org/a-frontend"),
        List(
          Link("open", "http://open/a-frontend")
        )
      )
    }
  }
}
