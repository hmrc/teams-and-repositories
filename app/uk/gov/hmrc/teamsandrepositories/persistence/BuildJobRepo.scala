/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes, UpdateOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.BuildJob
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.result.UpdateResult

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BuildJobRepo @Inject()(
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository(
      mongoComponent = mongoComponent,
      collectionName = "jenkinsLinks",
      domainFormat   = BuildJob.mongoFormat,
      indexes        = Seq(IndexModel(Indexes.hashed("service"), IndexOptions().name("serviceIdx")))
    ) {

  def findByService(service: String): Future[Option[BuildJob]] =
    collection
      .find(equal("service", service))
      .first()
      .toFutureOption()

  def updateOne(buildJob: BuildJob): Future[UpdateResult] =
    collection
      .updateOne(
        filter  = equal("service", buildJob.service),
        update  = set("jenkinsURL", buildJob.jenkinsURL),
        options = UpdateOptions().upsert(true)
      )
      .toFuture()

  def update(buildJobs: Seq[BuildJob])(implicit ec: ExecutionContext): Future[Seq[UpdateResult]] =
    Future.traverse(buildJobs)(updateOne)

  def clearAllData(implicit ec: ExecutionContext): Future[Boolean] =
    collection.deleteMany(Document()).toFuture().map(_.wasAcknowledged())
}
