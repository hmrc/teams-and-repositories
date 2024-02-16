/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.persistence

import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{DeleteOneModel, Filters, IndexModel, IndexOptions, Indexes, ReplaceOneModel, ReplaceOptions}
import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.models.TeamSummary
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitive

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamSummaryPersistence @Inject()(
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "teamSummaries",
  domainFormat = TeamSummary.mongoFormat,
  indexes = Seq(
    IndexModel(Indexes.ascending("name"), IndexOptions().collation(caseInsensitive).unique(true))
  )
){

  private val logger = Logger(this.getClass)

  // we don't need a ttl since the data is managed by updateTeamSummaries
  override lazy val requiresTtlIndex = false

  def updateTeamSummaries(teams: Seq[TeamSummary]): Future[Int] =
    for {
      oldTeams <- collection.find().map(_.name).toFuture().map(_.toSet)
      update   <- collection
                    .bulkWrite(teams.map(teamSummary => ReplaceOneModel(Filters.eq("name", teamSummary.name), teamSummary, ReplaceOptions().collation(caseInsensitive).upsert(true))))
                    .toFuture()
                    .map(_.getModifiedCount)
      toDelete =  (oldTeams -- teams.map(_.name)).toSeq
      _        <- if (toDelete.nonEmpty) {
                    logger.info(s"about to remove ${toDelete.length} deleted teams: ${toDelete.mkString(", ")}")
                    collection.bulkWrite(toDelete.map(team => DeleteOneModel(Filters.eq("name", team)))).toFuture()
                      .map(res => logger.info(s"removed ${res.getModifiedCount} deleted teams"))
                  } else Future.unit
    } yield update

  def findTeamSummaries(): Future[Seq[TeamSummary]] =
    collection
      .find()
      .sort(ascending("name"))
      .toFuture()

}
