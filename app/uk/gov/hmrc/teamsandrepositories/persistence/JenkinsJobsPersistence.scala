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

import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, Sorts}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
import uk.gov.hmrc.teamsandrepositories.models.{RepoType, TestType}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsJobsPersistence @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository[JenkinsJobsPersistence.Job](
  mongoComponent = mongoComponent
, collectionName = "jenkinsJobs"
, domainFormat   = JenkinsJobsPersistence.Job.mongoFormat
, indexes        = Seq(
                     IndexModel(Indexes.hashed("jobName"))
                   , IndexModel(Indexes.hashed("jobType"))
                   , IndexModel(Indexes.hashed("repoType"))
                   , IndexModel(Indexes.hashed("repoName"))
                   , IndexModel(Indexes.ascending("jenkinsURL"), IndexOptions().unique(true))
                   )
, replaceIndexes = true
, extraCodecs    = Codecs.playFormatSumCodecs(JenkinsJobsPersistence.JobType.format) ++
                   Codecs.playFormatSumCodecs(RepoType.format)
):
  import JenkinsJobsPersistence._

  // we replace all the data for each call to putAll
  override lazy val requiresTtlIndex = false

  def oldestServiceJob(): Future[Option[Job]] =
    collection
      .find(Filters.and(
        Filters.equal("jobType"     , JobType.Job)
      , Filters.equal("repoType"    , RepoType.Service)
      , Filters.exists("latestBuild", true)
      ))
      .sort(Sorts.ascending("latestBuild.timestamp"))
      .first()
      .toFutureOption()

  def findByJobName(name: String): Future[Option[Job]] =
    collection
      .find(Filters.equal("jobName", name))
      .first()
      .toFutureOption()

  def findAllByRepo(service: String): Future[Seq[Job]] =
    collection
      .find(Filters.equal("repoName", service))
      .toFuture()

  def findAllByJobType(jobType: JobType): Future[Seq[Job]] =
    collection
      .find(Filters.equal("jobType", jobType))
      .toFuture()

  def putAll(buildJobs: Seq[Job])(using ExecutionContext): Future[Unit] =
    MongoUtils.replace[Job](
      collection  = collection,
      newVals     = buildJobs,
      compareById = (a, b) => a.jenkinsUrl == b.jenkinsUrl,
      filterById  = entry => Filters.equal("jenkinsURL", entry.jenkinsUrl)
    ).map(_ => ())

object JenkinsJobsPersistence:
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

  case class Job(
    repoName   : String
  , jobName    : String
  , jenkinsUrl : String
  , jobType    : JobType
  , repoType   : Option[RepoType]
  , testType   : Option[TestType]
  , latestBuild: Option[JenkinsConnector.LatestBuild]
  )

  object Job:
    val mongoFormat: Format[Job] =
      given OFormat[JenkinsConnector.LatestBuild] =
        ( (__ \ "number"     ).format[Int]
        ~ (__ \ "url"        ).format[String]
        ~ (__ \ "timestamp"  ).format(MongoJavatimeFormats.instantFormat)
        ~ (__ \ "result"     ).formatNullable[JenkinsConnector.LatestBuild.BuildResult]
        ~ (__ \ "description").formatNullable[String]
        )(JenkinsConnector.LatestBuild.apply, l => Tuple.fromProductTyped(l))

      ( (__ \ "repoName"   ).format[String]
      ~ (__ \ "jobName"    ).format[String]
      ~ (__ \ "jenkinsURL" ).format[String]
      ~ (__ \ "jobType"    ).format[JobType](JobType.format)
      ~ (__ \ "repoType"   ).formatNullable[RepoType](RepoType.format)
      ~ (__ \ "testType"   ).formatNullable[TestType](TestType.format)
      ~ (__ \ "latestBuild").formatNullable[JenkinsConnector.LatestBuild]
      )(Job.apply, j => Tuple.fromProductTyped(j))

  enum JobType(val asString: String):
    case Job         extends JobType("job"         )
    case Test        extends JobType("test"        )
    case Pipeline    extends JobType("pipeline"    )
    case PullRequest extends JobType("pull-request")

  object JobType:
    def parse(s: String): Either[String, JobType] =
      values
        .find(_.asString.equalsIgnoreCase(s))
        .toRight(s"Invalid jobType - should be one of: ${values.map(_.asString).mkString(", ")}")

    val format: Format[JobType] =
      new Format[JobType] {
        override def reads(json: JsValue): JsResult[JobType] =
          json match
            case JsString(s) => parse(s).fold(msg => JsError(msg), x => JsSuccess(x))
            case _           => JsError("String value expected")

        override def writes(o: JobType): JsValue =
          JsString(o.asString)
      }
