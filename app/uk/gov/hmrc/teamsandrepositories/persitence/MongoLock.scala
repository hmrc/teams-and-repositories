package uk.gov.hmrc.teamsandrepositories.persitence

import com.google.inject.{Inject, Singleton}
import org.joda.time.Duration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}

class MongoLock(db: () => DB, lockId_ : String) extends LockKeeper {
  override def repo: LockRepository = LockMongoRepository(db)

  override def lockId: String = lockId_

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(20)
}

@Singleton
class MongoLocks @Inject()(mongo: ReactiveMongoComponent) {
  private val db = mongo.mongoConnector.db

  val dataReloadLock = new MongoLock(db, "data-reload-lock")
  val jenkinsLock    = new MongoLock(db, "jenkins-lock")
}
