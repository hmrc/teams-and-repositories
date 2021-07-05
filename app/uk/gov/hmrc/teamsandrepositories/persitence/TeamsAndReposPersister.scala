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

package uk.gov.hmrc.teamsandrepositories.persitence

import java.time.Instant

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.Filters.{elemMatch, equal, or, regex}
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._
import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.{ExecutionContext, Future}

import MongoTeamsAndRepositoriesPersister.caseInsensitiveCollation

class TeamsAndReposPersister @Inject()(mongoTeamsAndReposPersister: MongoTeamsAndRepositoriesPersister) {
  private val logger = Logger(this.getClass)

  def update(teamsAndRepositories: TeamRepositories)(implicit ec: ExecutionContext): Future[TeamRepositories] = {
    logger.info(
      s"Updating team record: ${teamsAndRepositories.teamName} (${teamsAndRepositories.repositories.size} repos)")
    mongoTeamsAndReposPersister.update(teamsAndRepositories)
  }

  def getAllTeamsAndRepos(archived: Option[Boolean]): Future[Seq[TeamRepositories]] =
    mongoTeamsAndReposPersister.getAllTeamAndRepos(archived)

  def getTeamsAndRepos(serviceNames: Seq[String]): Future[Seq[TeamRepositories]] =
    mongoTeamsAndReposPersister.getTeamsAndRepos(serviceNames)

  def clearAllData(implicit ec: ExecutionContext): Future[Unit] =
    mongoTeamsAndReposPersister.clearAllData

  def deleteTeams(teamNames: Set[String])(implicit ec: ExecutionContext): Future[Set[String]] = {
    logger.debug(s"Deleting orphan teams: $teamNames")
    Future.traverse(teamNames)(mongoTeamsAndReposPersister.deleteTeam)
  }

  def resetLastActiveDate(repoName: String)(implicit ec: ExecutionContext): Future[Option[Long]] =
    mongoTeamsAndReposPersister.resetLastActiveDate(repoName)
}

@Singleton
class MongoTeamsAndRepositoriesPersister @Inject()(
  mongoComponent: MongoComponent,
  futureHelpers : FutureHelpers
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "teamsAndRepositories",
  domainFormat   = TeamRepositories.mongoFormat,
  indexes        = Seq(
                     IndexModel(
                       Indexes.ascending("teamName"),
                       IndexOptions().name("teamNameIdx").collation(caseInsensitiveCollation).unique(true)
                     )
                   ),
  replaceIndexes = true
) {
  private val logger = Logger(this.getClass)

  def update(teamAndRepos: TeamRepositories)(implicit ec: ExecutionContext): Future[TeamRepositories] =
    futureHelpers
      .withTimerAndCounter("mongo.update") {
        collection
          .replaceOne(
            filter      = equal("teamName", teamAndRepos.teamName),
            replacement = teamAndRepos,
            options     = ReplaceOptions().upsert(true).collation(caseInsensitiveCollation)
          )
          .toFutureOption()
          .map(_ => teamAndRepos)
      }
      .recover {
        case lastError =>
          throw new RuntimeException(s"failed to persist $teamAndRepos", lastError)
      }

  def getTeamsAndRepos(serviceNames: Seq[String]): Future[Seq[TeamRepositories]] =
    collection
      .find(
        elemMatch(
          "repositories",
          or(
            serviceNames
              .map(serviceName => regex("name", "^" + serviceName + "$", "i")): _*
          )
        )
      )
      .toFuture()

  def getAllTeamAndRepos(archived: Option[Boolean]): Future[Seq[TeamRepositories]] =
    // We need to filter after retrieving from Mongo as unfortunately a Mongo projection
    // using $elemMatch will only return the first matching item in an array, not
    // all matching items
    collection
      .find()
      .toFuture()
      .map { unfilteredTeamsAndRepos =>
        archived match {
          case None    => unfilteredTeamsAndRepos
          case Some(a) => unfilteredTeamsAndRepos.map { teamAndRepo =>
                            teamAndRepo.copy(repositories = teamAndRepo.repositories.filter(_.archived == a))
                          }
        }
      }

  def clearAllData(implicit ec: ExecutionContext): Future[Unit] =
    collection
      .deleteMany(Document())
      .toFuture()
      .map(_ => ())

  def deleteTeam(teamName: String)(implicit ec: ExecutionContext): Future[String] =
    futureHelpers
      .withTimerAndCounter("mongo.cleanup") {
        collection
          .deleteOne(equal("teamName", teamName))
          .toFuture()
          .map(_ => teamName)
      }
      .recover {
        case lastError =>
          logger.error(s"Failed to remove $teamName", lastError)
          throw new RuntimeException(s"failed to remove $teamName")
      }

  def resetLastActiveDate(repoName: String)(implicit ec: ExecutionContext): Future[Option[Long]] =
    collection
      .updateMany(
        filter  = equal("repositories.name", repoName),
        update  = set("repositories.$.lastActiveDate", Instant.ofEpochMilli(0))
      )
      .toFuture()
      .map(
        _.getModifiedCount match {
          case 0        => None
          case modified => Some(modified)
        }
      )
}

object MongoTeamsAndRepositoriesPersister {
  val caseInsensitiveCollation: Collation =
    Collation.builder
      .locale("en")
      .collationStrength(CollationStrength.SECONDARY)
      .build
}
