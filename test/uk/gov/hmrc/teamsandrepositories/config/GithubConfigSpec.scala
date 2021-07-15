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

package uk.gov.hmrc.teamsandrepositories.config

import com.typesafe.config.ConfigFactory
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class GithubConfigSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "GithubConfig" should {
    "parse config correctly" in {
      val githubConfig = new GithubConfig(Configuration(
        "github.open.api.url"    -> "https://api.github.com"
      , "github.open.api.rawurl" -> "http://localhost:8461/github/raw"
      , "github.open.api.user"   -> "user1"
      , "github.open.api.key"    -> "token1"

      , "github.retry.count"        -> "5"
      , "github.retry.initialDelay" -> "50.millis"

      , "ratemetrics.githubtokens.1.username" -> "user1"
      , "ratemetrics.githubtokens.1.token"    -> "token1"
      , "ratemetrics.githubtokens.2.username" -> "user2"
      , "ratemetrics.githubtokens.2.token"    -> "token2"
      ))

      githubConfig.apiUrl shouldBe "https://api.github.com"
      githubConfig.rawUrl shouldBe "http://localhost:8461/github/raw"
      githubConfig.tokens shouldBe List("user1" -> "token1", "user2" -> "token2")
    }

    "infer token config from open api credentials" in {
      val config =
        ConfigFactory.parseString(
          f"""|
            |github.open.api.url     = "https://api.github.com"
            |github.open.api.rawurl  = "http://localhost:8461/github/raw"
            |github.open.api.user    = user1
            |github.open.api.key     = token1
            |github.retry.count        = 5
            |github.retry.initialDelay = 50.millis
            |ratemetrics.githubtokens.1.username = $${?github.open.api.user}
            |ratemetrics.githubtokens.1.token    = $${?github.open.api.key}
            """.stripMargin
        ).resolve
      val githubConfig = new GithubConfig(new Configuration(config))

      githubConfig.tokens shouldBe List("user1" -> "token1")
    }
  }
}
