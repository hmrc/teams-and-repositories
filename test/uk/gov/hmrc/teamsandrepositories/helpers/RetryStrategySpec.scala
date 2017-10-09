package uk.gov.hmrc.teamsandrepositories.helpers

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FreeSpec, Matchers}
import uk.gov.hmrc.githubclient.APIRateLimitExceededException

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RetryStrategySpec extends FreeSpec with Matchers with ScalaFutures with IntegrationPatience {

  class DummyThrower() {

    var retriedCount = 0

    def normalExceptionThrower: Future[Boolean] = {
      retriedCount += 1
      Future.failed(new RuntimeException)
    }

    def apiRateLimitThrower(throwAfterNumberOfCalls: Int): Future[Boolean] = {

      retriedCount += 1

      if(retriedCount == throwAfterNumberOfCalls)
        Future.failed(APIRateLimitExceededException(new RuntimeException))
      else
        Future.failed(new RuntimeException)
    }
  }

  "exponential retry" - {
    "should retry upto the max number of reties" in {

      val dummy = new DummyThrower

      whenReady(RetryStrategy.exponentialRetry(times = 5) {
        dummy.normalExceptionThrower
      }.failed) {e => e shouldBe an[RuntimeException]}

      dummy.retriedCount shouldBe 6

    }

    "should not retry if API rate limit exception is thrown" in {

      val dummy = new DummyThrower

      whenReady(RetryStrategy.exponentialRetry(times = 10) {
        dummy.apiRateLimitThrower(5)
      }.failed) {e => e shouldBe an[APIRateLimitExceededException]}

      dummy.retriedCount shouldBe 5

    }
  }

}
