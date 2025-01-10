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

import cats.Monad
import cats.syntax.all.*
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import scala.annotation.tailrec
import java.time.Instant

case class OpenPullRequest(
  repoName : String,
  title    : String,
  url      : String,
  author   : String,
  createdAt: Instant
)

object OpenPullRequest:

  private val monadJsResult: Monad[JsResult] =
    new Monad[JsResult]:
      override def flatMap[A, B](fa: JsResult[A])(f: A => JsResult[B]): JsResult[B] =
        fa.flatMap(f)

      @tailrec
      override def tailRecM[A, B](a: A)(f: A => JsResult[Either[A, B]]): JsResult[B] =
        f(a) match
          case JsSuccess(Left(a), _)  => tailRecM(a)(f)
          case JsSuccess(Right(b), _) => pure(b)
          case error: JsError         => error

      override def pure[A](x: A): JsResult[A] =
        JsSuccess(x)

  val seqReads: Reads[Seq[OpenPullRequest]] =
    given Monad[JsResult] = monadJsResult

    def prReads(repoName: String): Reads[OpenPullRequest] =
      ( Reads.pure(repoName)
      ~ (__ \ "title"           ).read[String]
      ~ (__ \ "url"             ).read[String]
      ~ (__ \ "author" \ "login").readNullable[String].map(_.getOrElse("Unknown"))
      ~ (__ \ "createdAt"       ).read[Instant]
      )(apply)

    (__ \ "name").read[String].flatMap[Seq[OpenPullRequest]]: repoName =>
      (__ \ "pullRequests" \ "nodes").read[Seq[JsObject]].flatMap: jos =>
         _ => jos.traverse(prReads(repoName).reads)

  val mongoFormat: Format[OpenPullRequest] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    ( (__ \ "repoName"   ).format[String]
    ~ (__ \ "title"      ).format[String]
    ~ (__ \ "url"        ).format[String]
    ~ (__ \ "author"     ).format[String]
    ~ (__ \ "createdAt"  ).format[Instant]
    )(apply, o => Tuple.fromProductTyped(o))

  val apiWrites: Writes[OpenPullRequest] =
    ( (__ \ "repoName" ).write[String]
    ~ (__ \ "title"    ).write[String]
    ~ (__ \ "url"      ).write[String]
    ~ (__ \ "author"   ).write[String]
    ~ (__ \ "createdAt").write[Instant]
    )(o => Tuple.fromProductTyped(o))
