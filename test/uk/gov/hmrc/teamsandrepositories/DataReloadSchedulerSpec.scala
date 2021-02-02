/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.ApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.mongo.lock.{LockRepository, MongoLockRepository, MongoLockService}
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class DataReloadSchedulerSpec
    extends AnyWordSpec
    with MockitoSugar
    with Results
    with OptionValues
    with GuiceOneServerPerSuite
    with Eventually
    with BeforeAndAfterAll {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .configure("metrics.jvm" -> false)
      .build()

  val mockMongoLockRepository: MongoLockRepository = mock[MongoLockRepository](RETURNS_DEEP_STUBS)
  val testMongoLocks: MongoLocks = new MongoLocks(mockMongoLockRepository) {
    override val dataReloadLock: MongoLockService = create
    override val jenkinsLock: MongoLockService    = create
    override val metrixLock: MongoLockService     = create

    private def create = new MongoLockService {
      override val lockRepository: LockRepository = mockMongoLockRepository
      override val lockId: String                 = "testLock"
      override val ttl: Duration                  = 20.minutes

      override def attemptLockWithRelease[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
        body.map(Some(_))(ec)

      override def attemptLockWithRefreshExpiry[T](body: => Future[T])(
        implicit ec: ExecutionContext): Future[Option[T]] =
        body.map(Some(_))(ec)
    }
  }

  "reload the cache and remove orphan teams at the configured intervals" in {

    val mockSchedulerConfigs  = mock[SchedulerConfigs](RETURNS_DEEP_STUBS)
    val mockPersistingService = mock[PersistingService]

    when(mockPersistingService.persistTeamRepoMapping(any())).thenReturn(Future(Nil))
    when(mockPersistingService.removeOrphanTeamsFromMongo(any())(any())).thenReturn(Future(Set.empty[String]))

    when(mockSchedulerConfigs.dataReloadScheduler.initialDelay).thenReturn(100.millisecond)
    when(mockSchedulerConfigs.dataReloadScheduler.interval).thenReturn(100.millisecond)
    when(mockSchedulerConfigs.dataReloadScheduler.enabled).thenReturn(true)

    new DataReloadScheduler(
      persistingService = mockPersistingService,
      config            = mockSchedulerConfigs,
      mongoLocks        = testMongoLocks
    )(actorSystem = app.actorSystem, applicationLifecycle = app.injector.instanceOf[ApplicationLifecycle])

    verify(mockPersistingService, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping(any())
    verify(mockPersistingService, Mockito.timeout(500).atLeast(2)).removeOrphanTeamsFromMongo(any())(any())
  }

  "reloading the cache" should {
    "be disabled" in {
      val mockSchedulerConfigs  = mock[SchedulerConfigs](RETURNS_DEEP_STUBS)
      val mockPersistingService = mock[PersistingService]

      when(mockPersistingService.persistTeamRepoMapping(any())).thenReturn(Future(Nil))
      when(mockPersistingService.removeOrphanTeamsFromMongo(any())(any())).thenReturn(Future(Set.empty[String]))

      when(mockSchedulerConfigs.dataReloadScheduler.initialDelay).thenReturn(100.millisecond)
      when(mockSchedulerConfigs.dataReloadScheduler.interval).thenReturn(100.millisecond)
      when(mockSchedulerConfigs.dataReloadScheduler.enabled).thenReturn(false)

      new DataReloadScheduler(
        persistingService = mockPersistingService,
        config            = mockSchedulerConfigs,
        mongoLocks        = testMongoLocks
      )(actorSystem = app.actorSystem, applicationLifecycle = app.injector.instanceOf[ApplicationLifecycle])

      verify(mockPersistingService, Mockito.timeout(500).times(0)).persistTeamRepoMapping(any())
      verify(mockPersistingService, Mockito.timeout(500).times(0)).removeOrphanTeamsFromMongo(any())(any())
    }
  }
}
