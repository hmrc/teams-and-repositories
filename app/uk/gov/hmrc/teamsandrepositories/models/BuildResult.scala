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

package uk.gov.hmrc.teamsandrepositories.models

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

sealed trait BuildResult { def asString: String }

object BuildResult {
  case object Failure extends BuildResult { override val asString = "FAILURE" }
  case object Success extends BuildResult { override val asString = "SUCCESS" }
  case object Aborted extends BuildResult { override val asString = "ABORTED" }
  case object Unstable extends BuildResult { override val asString = "UNSTABLE" }
  case object Other extends BuildResult { override val asString = "Other" }


  val values: List[BuildResult] = List(Failure, Success, Aborted, Unstable, Other)

  def parse(s: String): BuildResult =
    values
      .find(_.asString.equalsIgnoreCase(s)).getOrElse(Other)


  implicit val format: Format[BuildResult] = new Format[BuildResult] {
    override def reads(json: JsValue): JsResult[BuildResult] =
      json match {
        case JsString(s) => JsSuccess(parse(s))
        case _ => JsError("String value expected")
      }

    override def writes(o: BuildResult): JsValue =
      JsString(o.asString)
  }
}