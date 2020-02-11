/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.persitence

import com.google.inject.{Inject, Singleton}
import org.joda.time.Duration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.lock.{LockMongoRepository, LockRepository}

case class LockKeeper(db: () => DB, lockId: String) extends uk.gov.hmrc.lock.LockKeeper {
  override val repo                 : LockRepository = LockMongoRepository(db)
  override val forceLockReleaseAfter: Duration       = Duration.standardMinutes(20)
}

case class ExclusiveTimePeriodLock(db: () => DB, lockId: String) extends uk.gov.hmrc.lock.ExclusiveTimePeriodLock {
  override val repo       : LockRepository = LockMongoRepository(db)
  override val holdLockFor: Duration       = Duration.standardMinutes(20)
}

@Singleton
class MongoLocks @Inject()(mongo: ReactiveMongoComponent) {
  private val db = mongo.mongoConnector.db

  val dataReloadLock = LockKeeper(db, "data-reload-lock")
  val jenkinsLock    = LockKeeper(db, "jenkins-lock")
  val metrixLock     = ExclusiveTimePeriodLock(db, "metrix-lock")
}
