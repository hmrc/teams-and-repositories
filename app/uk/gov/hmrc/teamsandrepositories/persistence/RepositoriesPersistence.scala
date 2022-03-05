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
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, TeamName}
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitiveCollation

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RepositoriesPersistence @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "repositories",
  domainFormat   = GitRepository.mongoFormat,
  indexes        = Seq(
    IndexModel(
      Indexes.ascending("name", "isArchived"),
      IndexOptions().name("nameAndArchivedIdx").collation(caseInsensitiveCollation).unique(true)
    )
  ),
  replaceIndexes = true
) {

  def clearAllData: Future[Unit] = collection.drop().toFuture().map(_ => ())


  def search(team: Option[String] = None, isArchived: Option[Boolean] = None): Future[Seq[GitRepository]] = {
    val filters = Seq( team.map(t => Filters.equal("teamNames", t)), isArchived.map(b => Filters.equal("isArchived", b))).flatten
    filters match {
      case Nil      => collection.find().toFuture()
      case more     => collection.find(Filters.and(more:_*)).toFuture()
    }
  }

  def findRepo(repoName: String): Future[Option[GitRepository]] =
    collection.find(filter = Filters.equal("name", repoName)).headOption()

  def findTeamNames(): Future[Seq[TeamName]] =
    collection
      .aggregate[BsonDocument](Seq(Aggregates.unwind("$teamNames"), Aggregates.group("$teamNames")))
      .map(t => TeamName(t.getString("_id").getValue))
      .toFuture()

  def updateRepos(repos: Seq[GitRepository]): Future[Int] = {
    collection
      .bulkWrite(repos.map(repo => ReplaceOneModel(Filters.eq("name", repo.name), repo, ReplaceOptions().collation(caseInsensitiveCollation).upsert(true))))
      .toFuture()
      .map(_.getModifiedCount)
  }
}
