/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration

import scala.collection.immutable.ListMap

class GithubConfigSpec extends WordSpec with Matchers with MockitoSugar {

  "GithubConfig" should {
    "parse config correctly" in {
      val config = new GithubConfig(Configuration(
        "github.open.api.url"    -> "https://api.github.com"
      , "github.open.api.rawurl" -> "http://localhost:8461/github/raw"
      , "github.open.api.user"   -> "user1"
      , "github.open.api.key"    -> "token1"

      , "githubtokens.1.username" -> "user1"
      , "githubtokens.1.token"    -> "token1"
      , "githubtokens.2.username" -> "user2"
      , "githubtokens.2.token"    -> "token2"
      ))

      config.url shouldBe "https://api.github.com"
      config.rawUrl shouldBe "http://localhost:8461/github/raw"
      config.tokens shouldBe List("user1" -> "token1", "user2" -> "token2")
    }
  }
}
