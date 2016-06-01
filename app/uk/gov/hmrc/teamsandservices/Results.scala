/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandservices

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import play.api.libs.json.{Json, Writes}
import play.api.mvc.Result

object Results {

  val CacheTimestampHeaderName = "X-Cache-Timestamp"

  def OkWithCachedTimestamp[T](cachedList:CachedResult[T])(implicit jsonWrites : Writes[T]): Result ={

    def format(dateTime: LocalDateTime):String = {
      DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(dateTime, ZoneId.of("GMT")))
    }

    play.api.mvc.Results.Ok(Json.toJson(cachedList.data))
      .withHeaders(CacheTimestampHeaderName -> format(cachedList.time))
  }
}
