/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FutureExtrasSpec extends AnyWordSpec with Matchers with ScalaFutures with IntegrationPatience {

  "FutureOfBoolean ||" should {
    "short circuit if needed" in {
      import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras.FutureOfBoolean

      var counter = 0

      def delayedF1 = Future {
        Thread.sleep(50); counter += 1; false
      }

      def delayedF2 = Future {
        Thread.sleep(50); counter += 1; true
      }

      def delayedF3 = Future {
        Thread.sleep(50); counter += 1; false
      }

      (delayedF1 || delayedF2 || delayedF3).futureValue shouldBe true

      counter shouldBe 2
    }

    "execute all if needed" in {
      import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras.FutureOfBoolean

      var counter = 0

      def delayedF1 = Future {
        Thread.sleep(50); counter += 1; false
      }

      def delayedF2 = Future {
        Thread.sleep(50); counter += 1; false
      }

      def delayedF3 = Future {
        Thread.sleep(50); counter += 1; false
      }

      (delayedF1 || delayedF2 || delayedF3).futureValue shouldBe false

      counter shouldBe 3
    }
  }

  "FutureOfBoolean &&" should {
    "short circuit if needed" in {
      import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras.FutureOfBoolean

      var counter = 0

      def delayedF1 = Future {
        Thread.sleep(50); counter += 1; true
      }

      def delayedF2 = Future {
        Thread.sleep(50); counter += 1; false
      }

      def delayedF3 = Future {
        Thread.sleep(50); counter += 1; true
      }

      (delayedF1 && delayedF2 && delayedF3).futureValue shouldBe false

      counter shouldBe 2
    }

    "execute all if needed" in {
      import uk.gov.hmrc.teamsandrepositories.helpers.FutureExtras.FutureOfBoolean

      var counter = 0

      def delayedF1 = Future {
        Thread.sleep(50); counter += 1; false
      }

      def delayedF2 = Future {
        Thread.sleep(50); counter += 1; false
      }

      def delayedF3 = Future {
        Thread.sleep(50); counter += 1; false
      }

      (delayedF1 || delayedF2 || delayedF3).futureValue shouldBe false

      counter shouldBe 3
    }
  }

}
