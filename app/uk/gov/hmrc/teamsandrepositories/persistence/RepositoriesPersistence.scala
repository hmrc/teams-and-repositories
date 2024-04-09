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

import org.bson.conversions.Bson
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Aggregates.{`match`, addFields, group, sort, unwind}
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, CollectionFactory, PlayMongoRepository}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag, TeamRepositories}
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitive
import org.mongodb.scala.model.Accumulators.{addToSet, first, max, min}
import org.mongodb.scala.model.Filters.equal
import uk.gov.hmrc.teamsandrepositories.connectors.BranchProtection

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RepositoriesPersistence @Inject()(
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "repositories",
  domainFormat   = GitRepository.mongoFormat,
  indexes        = Seq(
                       IndexModel(Indexes.ascending("name"), IndexOptions().background(true).collation(caseInsensitive).unique(true)),
                       IndexModel(Indexes.ascending("repoType"), IndexOptions().background(true)),
                       IndexModel(Indexes.ascending("serviceType"), IndexOptions().background(true)),
                       IndexModel(Indexes.ascending("isArchived"), IndexOptions().background(true))
                      ),
  replaceIndexes = true
) {
  // updateRepos cleans up unreferenced teams
  override lazy val requiresTtlIndex = false

  private val legacyCollection: MongoCollection[TeamRepositories] =
    CollectionFactory.collection(mongoComponent.database, collectionName, TeamRepositories.mongoFormat)

  private val Quoted = """^\"(.*)\"$""".r

  def find(
    name       : Option[String]      = None,
    team       : Option[String]      = None,
    owningTeam : Option[String]      = None,
    isArchived : Option[Boolean]     = None,
    repoType   : Option[RepoType]    = None,
    serviceType: Option[ServiceType] = None,
    tags       : Option[List[Tag]]   = None,
  ): Future[Seq[GitRepository]] = {
    val filters = Seq(
      name       .map {
                   case Quoted(n) => Filters.equal("name", n)
                   case n         => Filters.regex("name", n)
                 },
      team       .map(t  => Filters.equal("teamNames"  , t)),
      owningTeam .map(t  => Filters.equal("owningTeams", t)),
      isArchived .map(b  => Filters.equal("isArchived" , b)),
      repoType   .map(rt => Filters.equal("repoType"   , rt.asString)),
      serviceType.map(st => Filters.equal("serviceType", st.asString)),
      tags       .map(ts => Filters.and(ts.map(t => Filters.equal("tags", t.asString)): _*)),
    ).flatten

    collection
      .find(if (filters.isEmpty) Filters.empty() else Filters.and(filters: _*))
      .toFuture()
  }

  def findRepo(repoName: String): Future[Option[GitRepository]] =
    collection
      .find(filter = Filters.equal("name", repoName)).headOption()

  def putRepos(repos: Seq[GitRepository]): Future[Int] =
    collection
      .bulkWrite(repos.map(repo =>
        ReplaceOneModel(
          Filters.eq("name", repo.name),
          repo,
          ReplaceOptions().collation(caseInsensitive).upsert(true)
        )
      ))
      .toFuture()
      .map(_.getModifiedCount)

  def putRepo(repos: GitRepository): Future[Unit] =
    putRepos(Seq(repos))
      .map(_ => ())

  def archiveRepo(repoName: String): Future[Unit] =
    collection
      .updateOne(
        filter = equal("name", repoName),
        update = Updates.set("isArchived", true)
      )
      .toFuture()
      .map(_ => ())

  def deleteRepo(repoName: String): Future[Unit] =
    collection
      .deleteOne(
        filter = equal("name", repoName)
      )
      .toFuture()
      .map(_ => ())

  // This exists to provide backward compatible data to the old API. Dont use it in new functionality!
  def getAllTeamsAndRepos(archived: Option[Boolean]): Future[Seq[TeamRepositories]] =
    legacyCollection
      .aggregate(Seq(
        `match`(archived.fold[Bson](Filters.empty())(a => Filters.eq("isArchived", a))),
        unwind("$teamNames"),
        addFields(Field("teamid", "$teamNames"), Field("teamNames", BsonArray())),
        group(
          id = "$teamid",
          first("teamName", "$teamid"),
          addToSet("repositories", "$$ROOT"),
          min("createdDate", "$createdDate"),
          max("updateDate", "$lastActiveDate")
        ),
        sort(Sorts.ascending("_id"))
      ))
      .toFuture()

  def updateRepoBranchProtection(repoName: String, branchProtection: Option[BranchProtection]): Future[Unit] = {
    implicit val bpf = BranchProtection.format
    collection
      .updateOne(
        filter = Filters.equal("name", repoName),
        update = Updates.set("branchProtection", Codecs.toBson(branchProtection))
      )
      .toFuture()
      .map(_ => ())
  }
}
