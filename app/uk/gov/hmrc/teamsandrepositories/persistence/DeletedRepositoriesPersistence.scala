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

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{DeleteOptions, Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.teamsandrepositories.models.{DeletedGitRepository, RepoType, ServiceType}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeletedRepositoriesPersistence @Inject()(
  val mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent,
  collectionName = "deleted-repositories",
  domainFormat   = DeletedGitRepository.mongoFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("name"),        IndexOptions().name("nameIdx").collation(Collations.caseInsensitive).unique(true)),
                     IndexModel(Indexes.ascending("owningTeams"), IndexOptions().name("teamIdx")),
                     IndexModel(Indexes.ascending("repoType"),    IndexOptions().name("repoTypeIdx"))
                   )
) with Transactions:

  // need to keep permanent record of deleted repositories
  override lazy val requiresTtlIndex = false

  private given TransactionConfiguration = TransactionConfiguration.strict

  private val Quoted = """^\"(.*)\"$""".r

  def find(
    name       : Option[String]      = None
  , team       : Option[String]      = None
  , repoType   : Option[RepoType]    = None
  , serviceType: Option[ServiceType] = None
  ): Future[Seq[DeletedGitRepository]] =
    collection
      .find(
        Seq(
          name.map:
            case Quoted(n) => Filters.equal("name", n)
            case n         => Filters.regex("name", n),
          team       .map(tm => Filters.equal("owningTeams", tm         )),
          repoType   .map(rt => Filters.equal("repoType"   , rt.asString)),
          serviceType.map(st => Filters.equal("serviceType", st.asString))
        ).flatten
         .foldLeft(Filters.empty())(Filters.and(_, _))
      )
      .collation(name.fold(Collations.default)(_ => Collations.caseInsensitive))
      .toFuture()

  def putRepo(repo: DeletedGitRepository): Future[Unit] =
    collection
      .insertOne(repo)
      .toFuture()
      .map(_ => ())

  def putAll(repos: Seq[DeletedGitRepository]): Future[Unit] =
    withSessionAndTransaction(session =>
      for
        _ <- collection.deleteMany(session, Document()).toFuture()
        _ <- if repos.nonEmpty then collection.insertMany(session, repos).toFuture()
             else Future.successful(())
      yield ()
    )


  // Remove when repo has been recreated - added to repositories collection
  def deleteRepos(repoNames: Seq[String]): Future[Long] =
    if repoNames.isEmpty then
      Future.successful(0)
    else
      collection
        .deleteMany(
          Filters.or(repoNames.map(name => Filters.eq("name", name)): _*)
        , DeleteOptions().collation(Collations.caseInsensitive)
        )
        .toFuture()
        .map(_.getDeletedCount)
