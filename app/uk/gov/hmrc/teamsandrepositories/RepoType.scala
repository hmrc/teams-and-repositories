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

package uk.gov.hmrc.teamsandrepositories

import play.api.libs.json._

// TODO convert to AST
object RepoType extends Enumeration {

  type RepoType = Value

  val Service, Library, Prototype, Other = Value

  implicit val repoType: Format[RepoType] = new Format[RepoType] {
    override def reads(json: JsValue): JsResult[RepoType] = json match {
      case JsString(s) =>
        try {
          JsSuccess(RepoType.withName(s))
        } catch {
          case _: NoSuchElementException =>
            JsError(
              s"Enumeration expected of type: '${RepoType.getClass}', but it does not appear to contain the value: '$s'")
        }
      case _ => JsError("String value expected")
    }

    override def writes(o: RepoType): JsValue = JsString(o.toString)
  }

}
