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
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag}
import uk.gov.hmrc.teamsandrepositories.persistence.Collations.caseInsensitive
import uk.gov.hmrc.teamsandrepositories.connectors.BranchProtection
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import play.api.libs.json.Format

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RepositoriesPersistence @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "repositories",
  domainFormat   = GitRepository.mongoFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("name"), IndexOptions().background(true).collation(caseInsensitive).unique(true)),
                     IndexModel(Indexes.ascending("repoType"), IndexOptions().background(true)),
                     IndexModel(Indexes.ascending("serviceType"), IndexOptions().background(true)),
                     IndexModel(Indexes.ascending("isArchived"), IndexOptions().background(true)),
                     IndexModel(Indexes.ascending("owningTeams"), IndexOptions().background(true)),
                     IndexModel(Indexes.ascending("digitalServiceName"), IndexOptions().background(true).sparse(true))
                   ),
  replaceIndexes = true
):
  // updateRepos cleans up unreferenced teams
  override lazy val requiresTtlIndex = false

  private val Quoted = """^\"(.*)\"$""".r

  def find(
            name              : Option[String]      = None,
            team              : Option[String]      = None,
            owningTeam        : Option[String]      = None,
            digitalServiceName: Option[String]      = None,
            isArchived        : Option[Boolean]     = None,
            repoType          : Option[RepoType]    = None,
            serviceType       : Option[ServiceType] = None,
            tags              : Option[List[Tag]]   = None,
          ): Future[Seq[GitRepository]] =
    val filters = Seq(
      name              .map:
        case Quoted(n) => Filters.equal("name", n)
        case n         => Filters.regex("name", n),
      team              .map(t  => Filters.equal("teamNames"  ,        t)),
      owningTeam        .map(t  => Filters.equal("owningTeams",        t)),
      digitalServiceName.map(t  => Filters.equal("digitalServiceName", t)),
      isArchived        .map(b  => Filters.equal("isArchived" ,        b)),
      repoType          .map(rt => Filters.equal("repoType"   ,        rt.asString)),
      serviceType       .map(st => Filters.equal("serviceType",        st.asString)),
      tags              .map(ts => Filters.and(ts.map(t => Filters.equal("tags", t.asString)): _*)),
    ).flatten

    collection
      .find(if filters.isEmpty then Filters.empty() else Filters.and(filters: _*))
      .collation(name.fold(Collations.default)(_ => Collations.caseInsensitive))
      .toFuture()

  def findRepo(repoName: String): Future[Option[GitRepository]] =
    collection
      .find(Filters.equal("name", repoName))
      .collation(caseInsensitive)
      .headOption()

  def putRepos(repos: Seq[GitRepository]): Future[Int] =
    collection
      .bulkWrite(repos.map(repo =>
        ReplaceOneModel(
          Filters.equal("name", repo.name),
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
        filter  = Filters.equal("name", repoName),
        update  = Updates.set("isArchived", true),
        options = UpdateOptions().collation(caseInsensitive)
      )
      .toFuture()
      .map(_ => ())

  def deleteRepo(repoName: String): Future[Unit] =
    collection
      .deleteMany(
        filter  = Filters.equal("name", repoName),
        options = DeleteOptions().collation(caseInsensitive)
      )
      .toFuture()
      .map(_ => ())

  def getDigitalServiceNames: Future[Seq[String]] =
    collection.distinct[String]("digitalServiceName")
      .toFuture()

  def updateRepoBranchProtection(repoName: String, branchProtection: Option[BranchProtection]): Future[Unit] =
    given Format[BranchProtection] = BranchProtection.format
    collection
      .updateOne(
        filter  = Filters.equal("name", repoName),
        update  = Updates.set("branchProtection", Codecs.toBson(branchProtection)),
        options = UpdateOptions().collation(caseInsensitive)
      )
      .toFuture()
      .map(_ => ())
