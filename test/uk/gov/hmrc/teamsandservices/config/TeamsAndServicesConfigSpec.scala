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

package uk.gov.hmrc.teamsandservices.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.{Configuration, GlobalSettings}
import play.api.test.{FakeApplication, WithApplication}


class TeamsAndServicesConfigSpec extends WordSpec with Matchers {

  trait Setup extends WithApplication {

    def templatesConfig: String =
      """
        |{url-templates : {
        |  ci-closed : [
        |    {
        |      name: "ci-closed1"
        |      url: "http://closed1/$name"
        |    },
        |    {
        |      name: "ci-closed2"
        |      url: "http://closed2/$name"
        |    }]
        |  ci-open : [
        |    {
        |      name: "ci-open1"
        |      url: "http://open1/$name"
        |    },
        |    {
        |      name: "ci-open2"
        |      url: "http://open2/$name"
        |    }]
        |}}
      """.stripMargin

    def globalSettings = new GlobalSettings {
      override def configuration: Configuration = Configuration(ConfigFactory.parseString(templatesConfig))
    }

    override val app = FakeApplication(withGlobal = Some(globalSettings))
  }

  "ciUrlTemplates" should {
    "return all the url templates" in new Setup {
      val conf = new UrlTemplatesProvider() {}
      val templates: UrlTemplates = conf.ciUrlTemplates
      templates.ciClosed shouldBe Seq(UrlTemplate("ci-closed1", "http://closed1/$name"), UrlTemplate("ci-closed2", "http://closed2/$name"))
      templates.ciOpen shouldBe Seq(UrlTemplate("ci-open1", "http://open1/$name"), UrlTemplate("ci-open2", "http://open2/$name"))
      templates.environments shouldBe Map(
        "env1" -> Seq(UrlTemplate("ser1", "http://ser1/$name"), UrlTemplate("ser2", "http://ser2/$name")),
        "env2" -> Seq(UrlTemplate("ser1", "http://ser1/$name"), UrlTemplate("ser2", "http://ser2/$name"))
      )
    }

  }

}
