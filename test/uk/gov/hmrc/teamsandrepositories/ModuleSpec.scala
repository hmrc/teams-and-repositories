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

package uk.gov.hmrc.teamsandrepositories

import com.google.inject.{Injector, Key, TypeLiteral}
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.mongo.lock.{LockRepository, LockService, MongoLockRepository}
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.persistence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.schedulers.DataReloadScheduler
import uk.gov.hmrc.teamsandrepositories.services.{PersistingService, TimeStamper}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ModuleSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with Results
    with OptionValues
    with GuiceOneServerPerSuite
    with Eventually {

  val mockMongoLockRepository: MongoLockRepository = mock[MongoLockRepository](RETURNS_DEEP_STUBS)
  val testMongoLocks: MongoLocks = new MongoLocks(mockMongoLockRepository) {
    override val dataReloadLock: LockService = create()
    override val jenkinsLock: LockService    = create()
    override val metrixLock: LockService = create()
    override val reloadLock: LockService = create()

    private def create() = new LockService {
      override val lockRepository: LockRepository = mockMongoLockRepository
      override val lockId: String                 = "testLock"
      override val ttl: Duration                  = 20.minutes

      override def withLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        body.map(Some(_))(ec)
    }
  }

  val testTimestamper = new TimeStamper

  val mockSchedulerConfigs: SchedulerConfigs = mock[SchedulerConfigs](RETURNS_DEEP_STUBS)
  val intervalDuration: FiniteDuration       = 100.millis

  when(mockSchedulerConfigs.dataReloadScheduler.initialDelay).thenReturn(intervalDuration)
  when(mockSchedulerConfigs.dataReloadScheduler.interval).thenReturn(intervalDuration)
  when(mockSchedulerConfigs.dataReloadScheduler.enabled).thenReturn(true)
  when(mockSchedulerConfigs.jenkinsScheduler.enabled).thenReturn(false)

  val mockPersistingService: PersistingService = mock[PersistingService]

  when(mockPersistingService.updateRepositories()(any())).thenReturn(Future.successful(1))

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .overrides(
        bind[SchedulerConfigs].toInstance(mockSchedulerConfigs),
        bind[TimeStamper].toInstance(testTimestamper),
        bind[PersistingService].toInstance(mockPersistingService),
        bind[MongoLocks].toInstance(testMongoLocks)
      )
      .overrides(new Module())
      .configure("metrics.jvm" -> false)
      .build()

  "load DataReloadScheduler eagerly which should result in reload of the cache at the configured intervals" in {

    val guiceInjector = app.injector.instanceOf(classOf[Injector])

    val key = Key.get(new TypeLiteral[DataReloadScheduler]() {})

    guiceInjector.getInstance(key).isInstanceOf[DataReloadScheduler] mustBe true
    verify(mockPersistingService, timeout(500).atLeast(2)).updateRepositories()(any())
  }
}
