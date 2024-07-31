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

package uk.gov.hmrc.teamsandrepositories.schedulers

import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.services.JenkinsReloadService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class JenkinsReloadScheduler @Inject()(
  jenkinsReloadService: JenkinsReloadService
, config              : SchedulerConfigs
, mongoLockRepository : MongoLockRepository
, timestampSupport    : TimestampSupport
)(using
  actorSystem         : ActorSystem
, applicationLifecycle: ApplicationLifecycle
) extends SchedulerUtils
  with Logging:

  given HeaderCarrier = HeaderCarrier()

  given ExecutionContext = actorSystem.dispatchers.lookup("scheduler-dispatcher")

  private val lockService =
    ScheduledLockService(
      lockRepository    = mongoLockRepository,
      lockId            = "jenkins-lock",
      timestampSupport  = timestampSupport,
      schedulerInterval = config.jenkinsScheduler.interval
    )

  scheduleWithLock("Jenkins Reloader", config.jenkinsScheduler, lockService) {
    for
      _ <- jenkinsReloadService.updateBuildAndPerformanceJobs()
      _ =  logger.info("Finished Jenkins Reloader")
    yield ()
  }
