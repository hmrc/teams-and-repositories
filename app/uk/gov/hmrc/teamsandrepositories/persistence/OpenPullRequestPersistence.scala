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

import org.mongodb.scala.model.*
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import play.api.libs.json.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.teamsandrepositories.model.OpenPullRequest
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitive

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OpenPullRequestPersistence @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "openPullRequests",
  domainFormat   = OpenPullRequest.mongoFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("url"     ), IndexOptions().collation(caseInsensitive).unique(true)),
                     IndexModel(Indexes.ascending("repoName"), IndexOptions().collation(caseInsensitive)),
                     IndexModel(Indexes.ascending("author"  ), IndexOptions().collation(caseInsensitive))
                   ),
  replaceIndexes = true
):
  // updateOpenPullRequests cleans up data
  override lazy val requiresTtlIndex = false

  def findOpenPullRequests(
    repoName: Option[String] = None,
    author  : Option[String] = None
  ): Future[Seq[OpenPullRequest]] =

    val filters = Seq(
      repoName.map(name => Filters.equal("repoName", name)),
      author  .map(name => Filters.equal("author", name))
    ).flatten

    collection
      .find(if filters.isEmpty then Filters.empty() else Filters.and(filters: _*))
      .collation(caseInsensitive)
      .toFuture()

  def putOpenPullRequests(openPrs: Seq[OpenPullRequest]): Future[Unit] =
    MongoUtils.replace[OpenPullRequest](
      collection  = collection,
      newVals     = openPrs,
      compareById = (a, b) => a.url == b.url,
      filterById  = pr => Filters.equal("url", pr.url),
      collation   = Collations.caseInsensitive
    ).map(_ => ())

  def putOpenPullRequest(openPr: OpenPullRequest): Future[Unit] =
    collection
      .replaceOne(
        filter      = Filters.equal("url" , openPr.url),
        replacement = openPr,
        options     = ReplaceOptions().collation(caseInsensitive).upsert(true)
      )
      .toFuture()
      .map(_ => ())

  def deleteOpenPullRequest(url: String): Future[Long] =
    collection
      .deleteOne(
        filter  = Filters.equal("url", url),
        options = DeleteOptions().collation(caseInsensitive)
      )
      .toFuture()
      .map(_.getDeletedCount)
