/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, LockService}

import scala.concurrent.duration.DurationInt

@Singleton
class MongoLocks @Inject()(mongoLockRepository: MongoLockRepository) {
  val dataReloadLock: LockService = LockService(mongoLockRepository, "data-reload-lock", 20.minutes)
  val jenkinsLock   : LockService = LockService(mongoLockRepository, "jenkins-lock"    , 20.minutes)
  val metrixLock    : LockService = LockService(mongoLockRepository, "metrix-lock"     , 20.minutes)
  val reloadLock    : LockService = LockService(mongoLockRepository, "reload-lock"     , 20.minutes)
}
