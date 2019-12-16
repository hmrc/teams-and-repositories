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
