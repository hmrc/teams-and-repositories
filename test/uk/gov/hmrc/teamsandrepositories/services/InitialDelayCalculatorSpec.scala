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

package uk.gov.hmrc.teamsandrepositories.services

import java.time.LocalDateTime

import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class InitialDelayCalculatorSpec extends FreeSpec with Matchers {

  "getMillisTillExecutionTime" - {
    "should calculate the next execution time" - {
      "when the next execution time is later on today" in {
        val nextExecutionTime = InitialDelayCalculator.getDurationTillExecutionTime(
          "19:30",
          LocalDateTime.now().withHour(19).withMinute(29).withSecond(0).withNano(0))

        nextExecutionTime shouldBe (1 minute)
      }

      "when the next execution time will be tomorrow because the execution time is passed for today" in {
        val nextExecutionTime = InitialDelayCalculator.getDurationTillExecutionTime(
          "19:30",
          LocalDateTime.now().withHour(19).withMinute(31).withSecond(0).withNano(0))

        nextExecutionTime shouldBe ((1 day) - (1 minute))
      }

    }
    "should throw an exception if the provided time string is not in the valid format" in {
      the[RuntimeException] thrownBy { InitialDelayCalculator.getDurationTillExecutionTime("16.40") } should have message "Schedule time must be in hh:mm format (provided value: 16.40)"
      the[RuntimeException] thrownBy { InitialDelayCalculator.getDurationTillExecutionTime("1640") }  should have message "Schedule time must be in hh:mm format (provided value: 1640)"
      the[RuntimeException] thrownBy { InitialDelayCalculator.getDurationTillExecutionTime("16-40") } should have message "Schedule time must be in hh:mm format (provided value: 16-40)"
    }
  }

}
