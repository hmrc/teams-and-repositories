/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import org.mongodb.scala.model.Accumulators.{addToSet, first, max, min}
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.models.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitiveCollation

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class LegacyPersistence  @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "repositories",
  domainFormat   = TeamRepositories.mongoFormat,
  indexes        = Seq(
    IndexModel(
      Indexes.ascending("name", "isArchived"),
      IndexOptions().name("nameAndArchivedIdx").collation(caseInsensitiveCollation).unique(true)
    )
  ),
  replaceIndexes = true
) {

  def getAllTeamsAndRepos(archived: Option[Boolean]): Future[Seq[TeamRepositories]] = {
    collection.aggregate(Seq(
        archived.map(a => Aggregates.`match`(Filters.eq("isArchived", a))).getOrElse( Aggregates.`match`(BsonDocument())),
        Aggregates.unwind("$teamNames"),
        Aggregates.addFields( Field("teamid", "$teamNames"), Field("teamNames", BsonArray())),
        Aggregates.group(id = "$teamid", first("teamName","$teamid"), addToSet("repositories", "$$ROOT"), min("createdDate", "$createdDate"), max("updateDate", "$lastActiveDate") ),
        Aggregates.sort(Sorts.ascending("_id"))
      )).toFuture()
  }

}
