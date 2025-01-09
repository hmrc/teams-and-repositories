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
    IndexModel(Indexes.ascending("url"), IndexOptions().background(true).collation(caseInsensitive).unique(true)),
    IndexModel(Indexes.ascending("repoName"), IndexOptions().background(true).collation(caseInsensitive)),
    IndexModel(Indexes.ascending("author"), IndexOptions().background(true).collation(caseInsensitive))
  ),
  replaceIndexes = true
):
  // updateOpenPullRequests cleans up data
  override lazy val requiresTtlIndex = false

  def getAllOpenPullRequests: Future[Seq[OpenPullRequest]] =
    collection
      .find()
      .toFuture()
    
  def findOpenPullRequestsByRepo(repoName: String): Future[Seq[OpenPullRequest]] =
    collection
      .find(Filters.equal("repoName", repoName))
      .collation(caseInsensitive)
      .toFuture()

  def findOpenPullRequestsByAuthor(author: String): Future[Seq[OpenPullRequest]] =
    collection
      .find(Filters.equal("author", author))
      .collation(caseInsensitive)
      .toFuture()
    
  def putOpenPullRequests(openPrs: Seq[OpenPullRequest]): Future[Int] =
    collection
      .bulkWrite(openPrs.map(openPr =>
        ReplaceOneModel(
          Filters.equal("url", openPr.url),
          openPr,
          ReplaceOptions().collation(caseInsensitive).upsert(true)
        )
      ))
      .toFuture()
      .map(_.getModifiedCount)

  def putOpenPullRequest(openPr: OpenPullRequest): Future[Unit] =
    putOpenPullRequests(Seq(openPr))
      .map(_ => ())

  def deleteOpenPullRequest(url: String): Future[Long] =
    deleteOpenPullRequests(Seq(url))

  def deleteOpenPullRequests(urls: Seq[String]): Future[Long] =
    collection
      .deleteMany(
        filter = Filters.in("url", urls: _*),
        options = DeleteOptions().collation(caseInsensitive)
      )
      .toFuture()
      .map(_.getDeletedCount)
