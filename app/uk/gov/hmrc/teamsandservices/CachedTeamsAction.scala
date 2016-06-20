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

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

import play.api.mvc.{ActionBuilder, Request, Result, WrappedRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TeamsRequest[A](val teams: Seq[TeamRepositories], val request: Request[A])
  extends WrappedRequest[A](request)

object CachedTeamsActionBuilder {

  val CacheTimestampHeaderName = "X-Cache-Timestamp"

  def apply(dataSource: () => Future[CachedResult[Seq[TeamRepositories]]]) = new ActionBuilder[TeamsRequest] {

    def invokeBlock[A](
                        request: Request[A],
                        block: TeamsRequest[A] => Future[Result]): Future[Result] = {

      dataSource().flatMap { cachedTeams =>

        val teamServices = cachedTeams.map { teams =>
          teams.map { s =>
            TeamRepositories(s.teamName, s.repositories.filter(_.deployable))
          }
        }

        block(new TeamsRequest(teamServices.data, request)).map { res =>
          res.withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
        }
      }
    }
  }

  private def format(dateTime: LocalDateTime): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(dateTime, ZoneId.of("GMT")))
  }
}
