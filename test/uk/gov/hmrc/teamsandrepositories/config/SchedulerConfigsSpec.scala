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

import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration

import scala.concurrent.duration._

class SchedulerConfigsSpec extends WordSpec with Matchers {

  "scheduler config" should {
    "load the config" in {
      val schedulerConfig =
        new SchedulerConfigs(Configuration(
          "cache.teams.reloadEnabled"   -> "true",
          "cache.teams.initialDelay"    -> "5.minutes",
          "cache.teams.duration"        -> "5.minutes",
          "cache.jenkins.reloadEnabled" -> "false",
          "cache.jenkins.initialDelay"  -> "10.minutes",
          "cache.jenkins.duration"      -> "20.seconds"
        ))
      schedulerConfig.dataReloadScheduler.enabled        shouldBe true
      schedulerConfig.dataReloadScheduler.initialDelay() shouldBe 5.minutes
      schedulerConfig.dataReloadScheduler.frequency()    shouldBe 5.minutes
      schedulerConfig.jenkinsScheduler.enabled           shouldBe false
      schedulerConfig.jenkinsScheduler.initialDelay()    shouldBe 10.minutes
      schedulerConfig.jenkinsScheduler.frequency()       shouldBe 20.seconds
    }
  }

}
