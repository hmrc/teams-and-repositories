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

package uk.gov.hmrc.teamsandrepositories.config

import com.typesafe.config.ConfigFactory
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.OneAppPerSuite
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder

import scala.collection.immutable.ListMap

class TeamsAndRepositoriesConfigSpec extends WordSpec with Matchers with OneAppPerSuite with MockitoSugar {

  def templatesConfig: String =
    """
        |{url-templates : {
        |  ci-closed : [
        |    {
        |      name: "ci-closed1"
        |      display-name: "closed 1"
        |      url: "http://closed1/$name"
        |    },
        |    {
        |      name: "ci-closed2"
        |      display-name: "closed 2"
        |      url: "http://closed2/$name"
        |    }]
        |  ci-open : [
        |    {
        |      name: "ci-open1"
        |      display-name: "open 1"
        |      url: "http://open1/$name"
        |    },
        |    {
        |      name: "ci-open2"
        |      display-name: "open 2"
        |      url: "http://open2/$name"
        |    }]
        |}}
      """.stripMargin

  implicit override lazy val app =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(Configuration(ConfigFactory.parseString(templatesConfig)))
      .configure(
        Map(
          "github.open.api.host"       -> "http://bla.bla",
          "github.open.api.user"       -> "",
          "github.open.api.key"        -> "",
          "github.enterprise.api.host" -> "http://bla.bla",
          "github.enterprise.api.user" -> "",
          "github.enterprise.api.key"  -> ""
        )
      )
      .build()

  "ciUrlTemplates" should {
    "return all the url templates" in {

      val conf                    = new UrlTemplatesProvider(app.configuration)
      val templates: UrlTemplates = conf.ciUrlTemplates
      templates.ciClosed shouldBe Seq(
        UrlTemplate("ci-closed1", "closed 1", "http://closed1/$name"),
        UrlTemplate("ci-closed2", "closed 2", "http://closed2/$name"))
      templates.ciOpen shouldBe Seq(
        UrlTemplate("ci-open1", "open 1", "http://open1/$name"),
        UrlTemplate("ci-open2", "open 2", "http://open2/$name"))
      templates.environments.toList should contain theSameElementsInOrderAs ListMap(
        "env1" -> Seq(
          UrlTemplate("ser1", "ser 1", "http://ser1/$name"),
          UrlTemplate("ser2", "ser 2", "http://ser2/$name")),
        "env2" -> Seq(
          UrlTemplate("ser1", "ser 1", "http://ser1/$name"),
          UrlTemplate("ser2", "ser 2", "http://ser2/$name"))
      ).toList
    }

  }

}
