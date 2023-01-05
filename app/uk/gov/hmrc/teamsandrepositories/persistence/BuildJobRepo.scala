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

import org.mongodb.scala.model.Filters.{equal, nin}
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes, ReplaceOptions}
import org.mongodb.scala.result.{DeleteResult, UpdateResult}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.models.JenkinsObject.BuildJob

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BuildJobRepo @Inject()(
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "jenkinsLinks",
  domainFormat   = BuildJob.mongoFormat,
  indexes        = Seq(IndexModel(Indexes.hashed("name"), IndexOptions().name("nameIdx")))
) {

  def findByJobName(name: String): Future[Option[BuildJob]] =
    collection
      .find(equal("name", name))
      .first()
      .toFutureOption()

  def findAllByRepo(service: String): Future[Seq[BuildJob]] =
    collection
        .find(equal("gitHubUrl", s"https://github.com/hmrc/$service.git"))
        .toFuture()

  def updateOne(buildJob: BuildJob): Future[UpdateResult] =
    collection
      .replaceOne(
        filter = equal("name", buildJob.name),
        replacement = buildJob,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()

  def update(buildJobs: Seq[BuildJob])(implicit ec: ExecutionContext): Future[Seq[UpdateResult]] =
    Future.traverse(buildJobs)(updateOne)

  def deleteIfNotInList(buildJobNames: Seq[String]): Future[DeleteResult] =
    collection
      .deleteMany(nin("name", buildJobNames: _*))
      .toFuture()
}
