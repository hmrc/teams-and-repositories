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
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.teamsandrepositories.persistence.TestRepoRelationshipsPersistence.TestRepoRelationship

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestRepoRelationshipsPersistence @Inject()(
  override val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[TestRepoRelationship](
  mongoComponent = mongoComponent
, collectionName = "testRepoRelationships"
, domainFormat   = TestRepoRelationshipsPersistence.TestRepoRelationship.mongoFormat
, indexes        = IndexModel(Indexes.hashed("testRepo"))    ::
                   IndexModel(Indexes.hashed("serviceRepo")) ::
                   Nil
) with Transactions {

  override lazy val requiresTtlIndex = false //data is refreshed by webhook events

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def putRelationships(serviceRepo: String, relationships: Seq[TestRepoRelationship]): Future[Unit] =
    withSessionAndTransaction { session =>
      for {
        _ <- collection.deleteMany(session, filter = Filters.equal("serviceRepo", serviceRepo)).toFuture()
        _ <- collection.insertMany(session, relationships).toFuture()
      } yield ()
    }

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

}

object TestRepoRelationshipsPersistence {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  final case class TestRepoRelationship(testRepo: String, serviceRepo: String)

  object TestRepoRelationship {
    val mongoFormat: Format[TestRepoRelationship] =
      ( (__ \ "testRepo"   ).format[String]
      ~ (__ \ "serviceRepo").format[String]
      )(TestRepoRelationship.apply, unlift(TestRepoRelationship.unapply))
  }
}
