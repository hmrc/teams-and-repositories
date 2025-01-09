/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class OpenPullRequest(
  repoName: String,
  title: String,
  url: String,
  author: String,
  createdAt: Instant
)

object OpenPullRequest:
  val reads: Reads[List[OpenPullRequest]] = Reads: json =>
    for
      repos            <- json.validate[Seq[JsObject]]
      openPullRequests =  repos.flatMap: repo =>
                            val repoName = (repo \ "name").as[String]
                            (repo \ "pullRequests" \ "nodes").as[Seq[JsObject]].map: pr =>
                              OpenPullRequest(
                                repoName = repoName,
                                title = (pr \ "title").as[String],
                                url = (pr \ "url").as[String],
                                author = (pr \ "author" \ "login").asOpt[String].getOrElse("Unknown"),
                                createdAt = (pr \ "createdAt").as[Instant]
                              )
    yield openPullRequests.toList

  val mongoFormat: OFormat[OpenPullRequest] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    ( (__ \ "repoName"   ).format[String]
    ~ (__ \ "title"      ).format[String]
    ~ (__ \ "url"        ).format[String]
    ~ (__ \ "author"     ).format[String]
    ~ (__ \ "createdAt"  ).format[Instant]
    )(apply, g => Tuple.fromProductTyped(g))