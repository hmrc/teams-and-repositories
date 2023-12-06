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

import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions, Indexes, IndexModel, IndexOptions}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DecommissionRepository @Inject()(
  val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[DecommissionRepository.ToBeDecommissioned](
  mongoComponent = mongoComponent,
  collectionName = "toBeDecommissioned",
  domainFormat   = DecommissionRepository.ToBeDecommissioned.format,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("repository"), IndexOptions().background(true).name("repositoryIdx"))
                   ),
) {

  override lazy val requiresTtlIndex = false

  def toBeDecommissioned(repository: String): Future[Unit] =
    collection
      .findOneAndReplace(
        Filters.eq("repository", repository),
        DecommissionRepository.ToBeDecommissioned(repository),
        options = FindOneAndReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())

  def isBeingDecommissioned(repository: String): Future[Boolean] =
    collection.find(Filters.eq("repository", repository))
      .limit(1)
      .headOption()
      .map(_.isDefined)
}

object DecommissionRepository {
  import play.api.libs.json._

  case class ToBeDecommissioned(
    repository: String
  )

  object ToBeDecommissioned {
    val format: Format[ToBeDecommissioned] = 
      (__ \ "repository").format[String].bimap(ToBeDecommissioned(_), _.repository)
  }
}

