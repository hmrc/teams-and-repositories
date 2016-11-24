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

package uk.gov.hmrc.teamsandrepositories

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import akka.actor.ActorSystem
import com.google.inject.Inject
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class DataSavingController @Inject()(dataLoader: MemoryCachedRepositoryDataSource[TeamRepositories],
                                            actorSystem: ActorSystem) extends BaseController {

  val CacheTimestampHeaderName = "X-Cache-Timestamp"


  implicit val environmentFormats = Json.format[Link]
  implicit val linkFormats = Json.format[Environment]
  implicit val serviceFormats = Json.format[RepositoryDetails]


  private def format(dateTime: LocalDateTime): String = {
    DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(dateTime, ZoneId.of("GMT")))
  }

  def save = Action.async { implicit request =>
    val file: Option[String] = request.getQueryString("file")

    file match {
      case Some(filename) =>
        dataLoader.getCachedTeamRepoMapping.map { cachedTeams =>
          import java.io._
          implicit val repositoryFormats = Json.format[Repository]
          implicit val teamRepositoryFormats = Json.format[TeamRepositories]
          val pw = new PrintWriter(new File(filename))
          pw.write(Json.stringify(Json.toJson(cachedTeams.data)))
          pw.close()

          Ok(s"Saved $file").withHeaders(CacheTimestampHeaderName -> format(cachedTeams.time))
        }
      case None =>
        Future(NotAcceptable(s"no file specified").withHeaders(CacheTimestampHeaderName -> format(LocalDateTime.now())))
    }

  }

}
