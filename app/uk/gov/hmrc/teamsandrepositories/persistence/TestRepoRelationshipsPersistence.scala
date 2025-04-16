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

import org.mongodb.scala.model.{Filters, IndexModel, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship
import org.mongodb.scala.ObservableFuture

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestRepoRelationshipsPersistence @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository[TestRepoRelationship](
  mongoComponent = mongoComponent
, collectionName = "testRepoRelationships"
, domainFormat   = TestRepoRelationshipsPersistence.TestRepoRelationship.mongoFormat
, indexes        = IndexModel(Indexes.hashed("testRepo"))    ::
                   IndexModel(Indexes.hashed("serviceRepo")) ::
                   Nil
):

  override lazy val requiresTtlIndex = false //data is refreshed by webhook events

  def putRelationships(serviceRepo: String, relationships: Seq[TestRepoRelationship]): Future[Unit] =
    MongoUtils.replace[TestRepoRelationship](
      collection    = collection,
      newVals       = relationships,
      oldValsFilter = Filters.equal("serviceRepo", serviceRepo),
      compareById   = (a, b) =>
                        a.serviceRepo == b.serviceRepo &&
                        a.testRepo    == b.testRepo,
      filterById    = entry =>
                        Filters.and(
                          Filters.equal("serviceRepo", entry.serviceRepo),
                          Filters.equal("testRepo"   , entry.testRepo)
                        )
    ).map(_ => ())

  def findTestReposByService(serviceRepo: String): Future[Seq[String]] =
    collection
      .find(Filters.equal("serviceRepo", serviceRepo))
      .toFuture()
      .map(_.map(_.testRepo))

  def findServicesByTestRepo(testRepo: String): Future[Seq[String]] =
    collection
      .find(Filters.equal("testRepo", testRepo))
      .toFuture()
      .map(_.map(_.serviceRepo))

  def deleteByRepo(repo: String): Future[Unit] =
    collection
      .deleteMany(
        Filters.or(
          Filters.equal("serviceRepo", repo),
          Filters.equal("testRepo", repo)
        )
      )
      .toFuture()
      .map(_ => ())

object TestRepoRelationshipsPersistence:
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  case class TestRepoRelationship(
    testRepo   : String,
    serviceRepo: String
  )

  object TestRepoRelationship:
    val mongoFormat: Format[TestRepoRelationship] =
      ( (__ \ "testRepo"   ).format[String]
      ~ (__ \ "serviceRepo").format[String]
      )(TestRepoRelationship.apply, t => Tuple.fromProductTyped(t))
