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

import java.time.{Duration, LocalDateTime}
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object InitialDelayCalculator {

  def getDurationTillExecutionTime(hourMinute: String, now: LocalDateTime = LocalDateTime.now()): FiniteDuration = {

    val executionTime = hourMinute.split(":").toList match {
      case (hour :: minute :: Nil) =>
        Try(LocalDateTime.now().withHour(hour.toString.toInt).withMinute(minute.toString.toInt).withSecond(0))
          .getOrElse(throwScheduleFormatError(hourMinute))
      case _ => throwScheduleFormatError(hourMinute)
    }

    val duration: Duration = {
      val timeTillExecution = Duration.between(now, executionTime)
      if (timeTillExecution.getSeconds < 0) {
        Duration.between(now, executionTime.plusHours(24))
      } else {
        timeTillExecution
      }
    }
    scala.concurrent.duration.Duration(duration.toMinutes, TimeUnit.MINUTES)
  }

  private def throwScheduleFormatError(hourMinute: String) =
    throw new RuntimeException(s"Schedule time must be in hh:mm format (provided value: $hourMinute)")
}
