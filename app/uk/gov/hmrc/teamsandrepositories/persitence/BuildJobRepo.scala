/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.persitence

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import scala.concurrent.ExecutionContext.Implicits.global
import reactivemongo.play.json.ImplicitBSONHandlers._


import scala.concurrent.Future

@Singleton
class BuildJobRepo @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[BuildJob, BSONObjectID] (
    collectionName = "jenkinsLinks",
    mongo          = mongoConnector.db,
    domainFormat   = BuildJob.mongoFormats) {

  override def indexes: Seq[Index] =
    Seq(Index(Seq("service" -> IndexType.Hashed), name = Some("serviceIdx")))


  def findByService(service: String): Future[Option[BuildJob]] = {
    find("service" -> service).map(_.headOption)
  }

  def updateOne(buildJob: BuildJob): Future[UpdateWriteResult] = {
    collection
      .update(
        selector = Json.obj("service" -> buildJob.service),
        update   = Json.obj("$set" -> Json.obj("jenkinsURL" -> buildJob.jenkinsURL)),
        upsert   = true
      )
  }

  def update(buildJobs: Seq[BuildJob]): Future[Seq[UpdateWriteResult]] =
    Future.traverse(buildJobs)(updateOne)
}
