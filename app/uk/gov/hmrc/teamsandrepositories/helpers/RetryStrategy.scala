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

package uk.gov.hmrc.teamsandrepositories.helpers

import java.util.{Timer, TimerTask}

import play.api.Logger
import uk.gov.hmrc.teamsandrepositories.connectors.{ApiAbuseDetectedException, ApiRateLimitExceededException}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration

object RetryStrategy {

  private val logger = Logger(this.getClass)

  private def schedule[T](delay: Duration)(eventualT: => Future[T]): Future[T] = {
    val promise = Promise[T]()
    new Timer().schedule(
      new TimerTask { override def run() = promise.completeWith(eventualT) },
      delay.toMillis
    )
    promise.future
  }

  def exponentialRetry[T](
    times: Int,
    delay: Duration
  )(f: => Future[T])(
    implicit executor: ExecutionContext): Future[T] =
    f.recoverWith {
      case e: ApiRateLimitExceededException =>
        logger.error(s"API rate limit is reached (skipping remaining $times retries)", e)
        Future.failed(e)

      case e: ApiAbuseDetectedException =>
        logger.error(s"API abuse detected (skipping remaining $times retries)", e)
        Future.failed(e)

      case e if times > 0 =>
        logger.error("error making request Retrying :", e)
        logger.debug(s"Retrying with delay $delay attempts remaining: ${times - 1}")
        schedule(delay) {
          exponentialRetry(times - 1, delay * 2)(f)
        }
    }
}
