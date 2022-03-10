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
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import org.mongodb.scala.model.Aggregates.{group, sort, unwind}
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{CollectionFactory, PlayMongoRepository}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamName, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitive
import org.mongodb.scala.model.Accumulators.{addToSet, first, max, min}
import org.mongodb.scala.model.Aggregates.{`match`, addFields, group, sort, unwind}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RepositoriesPersistence @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "repositories",
  domainFormat   = GitRepository.mongoFormat,
  indexes        = RepositoriesPersistence.indexes
) {

  val legacyCollection: MongoCollection[TeamRepositories] = CollectionFactory.collection(mongoComponent.database, collectionName, TeamRepositories.mongoFormat)

  def clearAllData: Future[Unit] = collection.drop().toFuture().map(_ => ())

  def search(team: Option[String] = None, isArchived: Option[Boolean] = None): Future[Seq[GitRepository]] = {
    val filters = Seq( team.map(t => Filters.equal("teamNames", t)), isArchived.map(b => Filters.equal("isArchived", b))).flatten
    filters match {
      case Nil  => collection.find().toFuture()
      case more => collection.find(Filters.and(more:_*)).toFuture()
    }
  }

  def findRepo(repoName: String): Future[Option[GitRepository]] =
    collection.find(filter = Filters.equal("name", repoName)).headOption()

  def findTeamNames(): Future[Seq[TeamName]] =
    collection
      .aggregate[BsonDocument](Seq(
        unwind("$teamNames"),
        group("$teamNames"),
        sort(Sorts.ascending("_id"))
      ))
      .map(t => TeamName(t.getString("_id").getValue))
      .toFuture()

  def updateRepos(repos: Seq[GitRepository]): Future[Int] = {
    collection
      .bulkWrite(repos.map(repo => ReplaceOneModel(Filters.eq("name", repo.name), repo, ReplaceOptions().collation(caseInsensitive).upsert(true))))
      .toFuture()
      .map(_.getModifiedCount)
  }

  // This exists to provide backward compatible data to the old API. Dont use it in new functionality!
  def getAllTeamsAndRepos(archived: Option[Boolean]): Future[Seq[TeamRepositories]] = {
    legacyCollection.aggregate(Seq(
      archived.map(a => `match`(Filters.eq("isArchived", a))).getOrElse( `match`(BsonDocument())),
      unwind("$teamNames"),
      addFields( Field("teamid", "$teamNames"), Field("teamNames", BsonArray())),
      group(id = "$teamid", first("teamName","$teamid"), addToSet("repositories", "$$ROOT"), min("createdDate", "$createdDate"), max("updateDate", "$lastActiveDate") ),
      sort(Sorts.ascending("_id"))
    )).toFuture()
  }

}

object RepositoriesPersistence {

  val indexes = Seq(
    IndexModel(
      Indexes.ascending("name", "isArchived"),
      IndexOptions().name("nameAndArchivedIdx").collation(caseInsensitive).unique(true)
    )
  )
}