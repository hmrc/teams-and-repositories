/*
 * Copyright 2023 HM Revenue & Customs
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

import org.bson.conversions.Bson
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.models._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeletedRepositoriesPersistence @Inject()(
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "deleted-repositories",
  domainFormat = DeletedGitRepository.mongoFormat,
  indexes = Seq(
    IndexModel(Indexes.ascending("name"), IndexOptions().name("nameIdx")),
    IndexModel(Indexes.ascending("owningTeams"), IndexOptions().name("teamIdx"))
  )
) {

  // need to keep permanent record of deleted repositories
  override lazy val requiresTtlIndex = false

  def set(repos: Seq[DeletedGitRepository]): Future[Unit] = {
    collection
      .insertMany(repos)
      .toFuture()
      .map(_ => ())
  }


  def find(name: Option[String], team: Option[String]): Future[Seq[DeletedGitRepository]] = {

    val nameFilter: Option[Bson]       = name.map(name => equal("name", name))
    val owningTeamFilter: Option[Bson] = team.map(team => equal("owningTeams", team))

    val filters = Seq(nameFilter, owningTeamFilter).flatten

    collection
      .find(if (filters.isEmpty) BsonDocument() else Filters.and(filters: _*))
      .toFuture()
  }
}
