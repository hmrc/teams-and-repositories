package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Inject, Singleton}
import play.api.Application
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB

import scala.concurrent.Future

@Singleton
class MongoConnector @Inject()(application: Application) {
  val db: () => DB = application.injector.instanceOf[ReactiveMongoComponent].mongoConnector.db
}


