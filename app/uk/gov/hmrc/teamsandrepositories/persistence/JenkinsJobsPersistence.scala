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
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, Sorts}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.transaction.{TransactionConfiguration, Transactions}
import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
import uk.gov.hmrc.teamsandrepositories.models.RepoType

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsJobsPersistence @Inject()(
  override val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository(
  mongoComponent = mongoComponent
, collectionName = "jenkinsJobs"
, domainFormat   = JenkinsJobsPersistence.Job.mongoFormat
, indexes        = IndexModel(Indexes.hashed("jobName"), IndexOptions().name("jobNameIdx")) ::
                   IndexModel(Indexes.hashed("jobType"))                                    ::
                   IndexModel(Indexes.hashed("repoType"))                                   ::
                   IndexModel(Indexes.hashed("repoName"))                                   ::
                   Nil
, extraCodecs    = Codecs.playFormatSumCodecs(JenkinsJobsPersistence.JobType.format) ++
                   Codecs.playFormatSumCodecs(RepoType.format)
) with Transactions {

  // we replace all the data for each call to putAll
  override lazy val requiresTtlIndex = false

  private implicit val tc: TransactionConfiguration = TransactionConfiguration.strict

  def oldestServiceJob(): Future[Option[JenkinsJobsPersistence.Job]] =
    collection
      .find(Filters.and(
        Filters.equal("jobType"     , JenkinsJobsPersistence.JobType.Job)
      , Filters.equal("repoType"    , RepoType.Service)
      , Filters.exists("latestBuild", true)
      ))
      .sort(Sorts.ascending("latestBuild.timestamp"))
      .first()
      .toFutureOption()

  def findByJobName(name: String): Future[Option[JenkinsJobsPersistence.Job]] =
    collection
      .find(Filters.equal("jobName", name))
      .first()
      .toFutureOption()

  def findAllByRepo(service: String): Future[Seq[JenkinsJobsPersistence.Job]] =
    collection
      .find(Filters.equal("repoName", service))
      .toFuture()

  def findAllByJobType(jobType: JenkinsJobsPersistence.JobType): Future[Seq[JenkinsJobsPersistence.Job]] =
    collection
      .find(Filters.equal("jobType", jobType))
      .toFuture()

  def putAll(buildJobs: Seq[JenkinsJobsPersistence.Job])(implicit ec: ExecutionContext): Future[Unit] =
     withSessionAndTransaction { session =>
      for {
        _ <- collection.deleteMany(session, BsonDocument()).toFuture()
        r <- collection.insertMany(session, buildJobs).toFuture()
      } yield ()
    }
}

object JenkinsJobsPersistence {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

  case class Job(
    repoName   : String
  , jobName    : String
  , jenkinsUrl : String
  , jobType    : JobType
  , repoType   : Option[RepoType]
  , latestBuild: Option[JenkinsConnector.LatestBuild]
  )

  object Job {
    val mongoFormat: Format[Job] = {
      implicit val latestBuildFormat: OFormat[JenkinsConnector.LatestBuild] =
        ( (__ \ "number"     ).format[Int]
        ~ (__ \ "url"        ).format[String]
        ~ (__ \ "timestamp"  ).format(MongoJavatimeFormats.instantFormat)
        ~ (__ \ "result"     ).formatNullable[JenkinsConnector.LatestBuild.BuildResult]
        ~ (__ \ "description").formatNullable[String]
        )(JenkinsConnector.LatestBuild.apply, unlift(JenkinsConnector.LatestBuild.unapply))

      ( (__ \ "repoName"   ).format[String]
      ~ (__ \ "jobName"    ).format[String]
      ~ (__ \ "jenkinsURL" ).format[String]
      ~ (__ \ "jobType"    ).format[JobType](JobType.format)
      ~ (__ \ "repoType"   ).formatNullable[RepoType](RepoType.format)
      ~ (__ \ "latestBuild").formatNullable[JenkinsConnector.LatestBuild]
      )(Job.apply, unlift(Job.unapply))
    }
  }

  sealed trait JobType { def asString: String }

  object JobType {
    case object Job         extends JobType { override val asString = "job"         }
    case object Pipeline    extends JobType { override val asString = "pipeline"    }
    case object PullRequest extends JobType { override val asString = "pull-request"}

    val values: List[JobType] =
      List(Job, Pipeline, PullRequest)

    def parse(s: String): Either[String, JobType] =
      values
        .find(_.asString.equalsIgnoreCase(s))
        .toRight(s"Invalid jobType - should be one of: ${values.map(_.asString).mkString(", ")}")

    val format = new Format[JobType] {
      override def reads(json: JsValue): JsResult[JobType] =
        json match {
          case JsString(s) => parse(s).fold(msg => JsError(msg), x => JsSuccess(x))
          case _           => JsError("String value expected")
        }

      override def writes(o: JobType): JsValue =
        JsString(o.asString)
    }
  }
}
