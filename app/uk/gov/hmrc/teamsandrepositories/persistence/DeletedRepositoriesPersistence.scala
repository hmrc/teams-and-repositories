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

import com.mongodb.client.model.Filters.{and => mAnd, eq => mEq}
import org.bson.conversions.Bson
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
  domainFormat   = DeletedGitRepository.mongoFormat,
  indexes        = Seq(
    IndexModel(Indexes.ascending("name"), IndexOptions().name("nameIdx"))
  )
) {

  override lazy val requiresTtlIndex = false

  def set(repos: Seq[DeletedGitRepository]): Future[Boolean] = {
    collection
      .insertMany(repos)
      .toFuture()
      .map(_.wasAcknowledged())
  }


  def get(name: Option[String]): Future[Seq[DeletedGitRepository]] = {

    val nameFilter: Option[Bson] = name.map(name => mEq("name", name))

    val filters = Seq(nameFilter).flatten

    val primaryFilter = Aggregates.filter(mAnd(filters: _*))

    val aggregates: Seq[Bson] = Seq(
      primaryFilter
    )

    filters match {
      case Nil => collection.find().toFuture()
      case _   => collection.aggregate(aggregates).toFuture()
    }
  }

  def removeByName(name: String): Future[Boolean] = {
    collection
      .deleteOne(Filters.equal("name", name))
      .toFuture()
      .map(_.wasAcknowledged())
  }
}
