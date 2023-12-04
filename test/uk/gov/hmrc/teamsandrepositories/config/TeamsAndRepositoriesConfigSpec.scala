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

package uk.gov.hmrc.teamsandrepositories.config

import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.collection.immutable.ListMap

class TeamsAndRepositoriesConfigSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        Map(
          "github.open.api.host" -> "http://bla.bla",
          "github.open.api.key"  -> "",
          "jenkins.username" -> "",
          "jenkins.token" -> "",
          "jenkins.url" -> ""
        )
      )
      .build()

  "ciUrlTemplates" should {
    "return all the url templates" in {
      val conf                    = new UrlTemplatesProvider(app.configuration)
      val templates: UrlTemplates = conf.ciUrlTemplates
      templates.environments.toList should contain theSameElementsInOrderAs ListMap(
        "Development" -> Seq(
          UrlTemplate("ser1", "ser 1", "http://ser1/$name"),
          UrlTemplate("ser2", "ser 2", "http://ser2/$name")),
        "Staging" -> Seq(
          UrlTemplate("ser1", "ser 1", "http://ser1/$name"),
          UrlTemplate("ser2", "ser 2", "http://ser2/$name"))
      ).toList
    }
  }
}
