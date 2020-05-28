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

package uk.gov.hmrc.teamsandrepositories.persitence

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONObjectID, BSONRegex}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.{ExecutionContext, Future}

class TeamsAndReposPersister @Inject()(
  mongoTeamsAndReposPersister: MongoTeamsAndRepositoriesPersister,
  futureHelpers: FutureHelpers) {

  private val logger = Logger(this.getClass)

  def update(teamsAndRepositories: TeamRepositories)(implicit ec: ExecutionContext): Future[TeamRepositories] = {
    logger.info(
      s"Updating team record: ${teamsAndRepositories.teamName} (${teamsAndRepositories.repositories.size} repos)")
    mongoTeamsAndReposPersister.update(teamsAndRepositories)
  }

  def getAllTeamsAndRepos(archived: Option[Boolean])(implicit ec: ExecutionContext): Future[Seq[TeamRepositories]] =
    mongoTeamsAndReposPersister.getAllTeamAndRepos(archived)

  def getTeamsAndRepos(serviceNames: Seq[String], archived: Option[Boolean])(implicit ec: ExecutionContext): Future[Seq[TeamRepositories]] =
    mongoTeamsAndReposPersister.getTeamsAndRepos(serviceNames, archived)

  def clearAllData(implicit ec: ExecutionContext): Future[Boolean] =
    mongoTeamsAndReposPersister.clearAllData

  def deleteTeams(teamNames: Set[String])(implicit ec: ExecutionContext): Future[Set[String]] = {
    logger.debug(s"Deleting orphan teams: $teamNames")
    Future.traverse(teamNames)(mongoTeamsAndReposPersister.deleteTeam)
  }

  def resetLastActiveDate(repoName: String)(implicit ec: ExecutionContext): Future[Option[Int]] =
    mongoTeamsAndReposPersister.resetLastActiveDate(repoName)

}

@Singleton
class MongoTeamsAndRepositoriesPersister @Inject()(mongoConnector: MongoConnector, futureHelpers: FutureHelpers)
    extends ReactiveRepository[TeamRepositories, BSONObjectID](
      collectionName = "teamsAndRepositories",
      mongo          = mongoConnector.db,
      domainFormat   = TeamRepositories.formats) {

  override def indexes: Seq[Index] =
    Seq(Index(Seq("teamName" -> IndexType.Hashed), name = Some("teamNameIdx")))

  def update(teamAndRepos: TeamRepositories)(implicit ec: ExecutionContext): Future[TeamRepositories] =
    futureHelpers
      .withTimerAndCounter("mongo.update") {
        collection
          .update(ordered = false)
          .one(
            q      = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)),
            u      = teamAndRepos,
            upsert = true)
          .map(_ => teamAndRepos)
      }
      .recover {
        case lastError =>
          throw new RuntimeException(s"failed to persist $teamAndRepos", lastError)
      }

  def getAllTeamAndRepos(archived: Option[Boolean])(implicit ec: ExecutionContext): Future[List[TeamRepositories]] = {
    findAll().map(withArchivedRepositoryFiltering(_, archived))
  }

  def getTeamsAndRepos(serviceNames: Seq[String], archived: Option[Boolean])(implicit ec: ExecutionContext): Future[List[TeamRepositories]] = {
    val serviceNamesJson =
      serviceNames.map(serviceName =>
        toJsFieldJsValueWrapper(Json.obj("name" -> BSONRegex("^" + serviceName + "$", "i"))))
    find("repositories" -> Json.obj("$elemMatch" -> Json.obj("$or" -> Json.arr(serviceNamesJson: _*))))
      .map(withArchivedRepositoryFiltering(_, archived))
  }

  def clearAllData(implicit ec: ExecutionContext): Future[Boolean] =
    super.removeAll().map(_.ok)

  def deleteTeam(teamName: String)(implicit ec: ExecutionContext): Future[String] =
    futureHelpers
      .withTimerAndCounter("mongo.cleanup") {
        collection
          .delete()
          .one(q = Json.obj("teamName" -> Json.toJson(teamName)))
          .map(_ => teamName)
      }
      .recover {
        case lastError =>
          logger.error(s"Failed to remove $teamName", lastError)
          throw new RuntimeException(s"failed to remove $teamName")
      }

  def resetLastActiveDate(repoName: String)(implicit ec: ExecutionContext): Future[Option[Int]] =
    collection
      .update(ordered=false)
      .one(
        q     = Json.obj("repositories.name" -> repoName),
        u     = Json.obj("$set" -> Json.obj("repositories.$.lastActiveDate" -> 0L)),
        multi = true
      )
      .map { result =>
        result.nModified match {
          case 0        => None
          case modified => Some(modified)
        }
      }

  private def withArchivedRepositoryFiltering(teamsAndRepos: List[TeamRepositories],
                                              archived: Option[Boolean]): List[TeamRepositories] =
    archived.map { a =>
      teamsAndRepos.map { teamAndRepo =>
        teamAndRepo.copy(repositories = teamAndRepo.repositories.filter(_.archived == a))
      }
    }.getOrElse(teamsAndRepos)

}
