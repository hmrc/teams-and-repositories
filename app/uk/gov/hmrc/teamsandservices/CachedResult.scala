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

import org.joda.time.DateTime
import play.api.libs.json._

class CachedResult[T](val data: T, val time: DateTime) {
  def map[B](f: T => B) = new CachedResult[B](f(this.data), this.time)
}

object CachedResult {
  import play.api.libs.functional.syntax._

  implicit def cachedResultFormats[T](implicit fmt: Format[T]): Format[CachedResult[T]] = new Format[CachedResult[T]] {
    private val writes: Writes[CachedResult[T]] = (
      (JsPath \ "data").write[T] and
        (JsPath \ "cacheTimestamp").write[DateTime]
      ) (x => (x.data, x.time))

    override def writes(o: CachedResult[T]): JsValue = writes.writes(o)
    override def reads(json: JsValue): JsResult[CachedResult[T]] = ???
  }
}
