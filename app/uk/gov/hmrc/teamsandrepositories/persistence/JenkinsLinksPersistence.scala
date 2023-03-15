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

import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.teamsandrepositories.models.JenkinsObject.BuildJob

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsLinksPersistence @Inject()(
  override val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "jenkinsLinks",
  domainFormat   = BuildJob.mongoFormat,
  indexes        = Seq(
                     IndexModel(Indexes.hashed("name"), IndexOptions().name("nameIdx")),
                     IndexModel(Indexes.hashed("gitHubUrl"))
                   )
) with Transactions {

  // we replace all the data for each call to putAll
  override lazy val requiresTtlIndex = false

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def findByJobName(name: String): Future[Option[BuildJob]] =
    collection
      .find(Filters.equal("name", name))
      .first()
      .toFutureOption()

  def findAllByRepo(service: String): Future[Seq[BuildJob]] =
    collection
      .find(Filters.equal("gitHubUrl", s"https://github.com/hmrc/$service.git"))
      .toFuture()

  def putAll(buildJobs: Seq[BuildJob])(implicit ec: ExecutionContext): Future[Unit] =
     withSessionAndTransaction { session =>
      for {
        _ <- collection.deleteMany(session, BsonDocument()).toFuture()
        r <- collection.insertMany(session, buildJobs).toFuture()
      } yield ()
    }
}
