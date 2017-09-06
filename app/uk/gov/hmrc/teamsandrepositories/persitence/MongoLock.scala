package uk.gov.hmrc.teamsandrepositories.persitence


import com.google.inject.{Inject, Singleton}
import org.joda.time.Duration
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}


@Singleton
class MongoLock @Inject()(mongoConnector: MongoConnector) extends LockKeeper {
  override def repo: LockRepository = LockMongoRepository(mongoConnector.db)

  override def lockId: String = "teams-and-repositories-sync-job"

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(20)
}
