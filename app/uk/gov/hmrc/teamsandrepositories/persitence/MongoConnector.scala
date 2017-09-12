package uk.gov.hmrc.teamsandrepositories.persitence

import com.google.inject.{Inject, Singleton}
import play.api.Application
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB

@Singleton
class MongoConnector @Inject()(application: Application) {
  val db: () => DB = application.injector.instanceOf[ReactiveMongoComponent].mongoConnector.db
}


