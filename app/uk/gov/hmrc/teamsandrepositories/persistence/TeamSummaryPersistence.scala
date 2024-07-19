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

import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, Sorts}
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.models.TeamSummary
import org.mongodb.scala.ObservableFuture

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamSummaryPersistence @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "teamSummaries",
  domainFormat   = TeamSummary.mongoFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("name"), IndexOptions().collation(Collations.caseInsensitive).unique(true))
                   )
) with Logging:

  // we don't need a ttl since the data is managed by updateTeamSummaries
  override lazy val requiresTtlIndex = false

  def updateTeamSummaries(teams: Seq[TeamSummary]): Future[Unit] =
    MongoUtils.replace[TeamSummary](
      collection    = collection,
      newVals       = teams,
      compareById   = (a, b) => a.name.toLowerCase == b.name.toLowerCase,
      filterById    = entry => Filters.equal("name", entry.name),
      collation     = Collations.caseInsensitive
    ).map:
       case (upserted, deleted) =>
         logger.info(s"Upserted $upserted and deleted $deleted teams")

  def findTeamSummaries(): Future[Seq[TeamSummary]] =
    collection
      .find()
      .sort(Sorts.ascending("name"))
      .toFuture()
