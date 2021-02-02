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

package uk.gov.hmrc.teamsandrepositories.helpers

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.githubclient.APIRateLimitExceededException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RetryStrategySpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience {

  class DummyThrower() {

    var retriedCount = 0

    def normalExceptionThrower: Future[Boolean] = {
      retriedCount += 1
      Future.failed(new RuntimeException)
    }

    def apiRateLimitThrower(throwAfterNumberOfCalls: Int): Future[Boolean] = {

      retriedCount += 1

      if (retriedCount == throwAfterNumberOfCalls)
        Future.failed(APIRateLimitExceededException(new RuntimeException))
      else
        Future.failed(new RuntimeException)
    }
  }

  "exponential retry" - {
    "should retry upto the max number of reties" in {

      val dummy = new DummyThrower

      whenReady(
        RetryStrategy
          .exponentialRetry(times = 5) {
            dummy.normalExceptionThrower
          }
          .failed) { e =>
        e shouldBe an[RuntimeException]
      }

      dummy.retriedCount shouldBe 6

    }

    "should not retry if API rate limit exception is thrown" in {

      val dummy = new DummyThrower

      whenReady(
        RetryStrategy
          .exponentialRetry(times = 10) {
            dummy.apiRateLimitThrower(5)
          }
          .failed) { e =>
        e shouldBe an[APIRateLimitExceededException]
      }

      dummy.retriedCount shouldBe 5
    }

  }

}
