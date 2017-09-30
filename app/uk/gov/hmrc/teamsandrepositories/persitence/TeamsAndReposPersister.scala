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

package uk.gov.hmrc.teamsandrepositories.persitence

import com.google.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class TeamsAndReposPersister @Inject()(mongoTeamsAndReposPersister: MongoTeamsAndRepositoriesPersister) {

  val teamsAndRepositoriesTimestampKeyName = "teamsAndRepositories.updated"
  lazy val logger = LoggerFactory.getLogger(this.getClass)

  def update(teamsAndRepositories: TeamRepositories): Future[TeamRepositories] = {
    logger.debug(s"Updating team record: ${teamsAndRepositories.teamName} (${teamsAndRepositories.repositories.size} repos)")
    mongoTeamsAndReposPersister.update(teamsAndRepositories)
  }

  def getAllTeamAndRepos: Future[Seq[TeamRepositories]] = {
      mongoTeamsAndReposPersister.getAllTeamAndRepos
  }


  def clearAllData: Future[Boolean] = {
    mongoTeamsAndReposPersister.clearAllData
  }

  def deleteTeams(teamNames: Set[String]): Future[Set[String]] = {
    logger.debug(s"Deleting orphan teams: $teamNames")
    Future.sequence(teamNames.map(mongoTeamsAndReposPersister.deleteTeam))
  }
}

@Singleton
class MongoTeamsAndRepositoriesPersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[TeamRepositories, BSONObjectID](
    collectionName = "teamsAndRepositories",
    mongo = mongoConnector.db,
    domainFormat = TeamRepositories.formats) {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("teamName" -> IndexType.Hashed), name = Some("teamNameIdx")))
      )
    )


  def update(teamAndRepos: TeamRepositories): Future[TeamRepositories] = {

    withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(selector = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)), update = teamAndRepos, upsert = true)
      } yield update match {
        case _ => teamAndRepos
      }
    } recover {
      case lastError => throw new RuntimeException(s"failed to persist $teamAndRepos", lastError)
    }
  }

  def add(teamsAndRepository: TeamRepositories): Future[Boolean] = {
    withTimerAndCounter("mongo.write") {
      insert(teamsAndRepository) map {
        case _ => true
      }
    } recover {
      case lastError =>
        logger.error(s"Could not add ${teamsAndRepository.teamName} to TeamsAndRepository collection", lastError)
        throw lastError
    }
  }

  def getAllTeamAndRepos: Future[List[TeamRepositories]] = findAll()

  def clearAllData: Future[Boolean] = super.removeAll().map(_.ok)

  def deleteTeam(teamName: String): Future[String] = {
    withTimerAndCounter("mongo.cleanup") {
      collection.remove(selector = Json.obj("teamName" -> Json.toJson(teamName))).map {
        case _ => teamName
      }
    } recover {
      case lastError =>
        logger.error(s"Failed to remove $teamName", lastError)
        throw new RuntimeException(s"failed to remove $teamName")
    }
  }
}




//!@@Singleton
//class MongoUpdateTimePersister @Inject()(mongoConnector: MongoConnector)
//  extends ReactiveRepository[KeyAndTimestamp, BSONObjectID](
//    collectionName = "updateTime",
//    mongo = mongoConnector.db,
//    domainFormat = KeyAndTimestamp.formats) {
//
//  private val keyFieldName = "keyName"
//
//  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
//    Future.sequence(
//      Seq(
//        collection.indexesManager.ensure(Index(Seq(keyFieldName -> IndexType.Hashed), name = Some(keyFieldName + "Idx")))
//      )
//    )
//
//  def get(keyName: String): Future[Option[KeyAndTimestamp]] = {
//    withTimerAndCounter("mongo.timestamp.get") {
//      collection.find(Json.obj(keyFieldName -> Json.toJson(keyName)))
//        .cursor[KeyAndTimestamp]()
//        .collect[List]().map(_.headOption)
//    }
//  }
//
//  def update(keyAndTimestamp: KeyAndTimestamp): Future[Boolean] = {
//    withTimerAndCounter("mongo.timestamp.update") {
//      for {
//        update <- collection.update(selector = Json.obj(keyFieldName -> Json.toJson(keyAndTimestamp.keyName)), update = keyAndTimestamp, upsert = true)
//      } yield update match {
//        case lastError if lastError.inError => throw lastError
//        case _ => true
//      }
//    }
//  }
//
//  def remove(keyName: String): Future[Boolean] = super.remove(keyFieldName -> Json.toJson(keyName)).map(!_.hasErrors)
//}


