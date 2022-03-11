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

package uk.gov.hmrc.teamsandrepositories.schedulers

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persistence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DataReloadScheduler @Inject()(
  persistingService: PersistingService,
  config          : SchedulerConfigs,
  mongoLocks      : MongoLocks
)(implicit
  actorSystem         : ActorSystem,
  applicationLifecycle: ApplicationLifecycle
) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val logger = Logger(this.getClass)

  implicit val ec: ExecutionContext = actorSystem.dispatchers.lookup("scheduler-dispatcher")

  scheduleWithLock("Teams and Repos Reloader", config.dataReloadScheduler, mongoLocks.dataReloadLock) {
    for {
      count <- persistingService.updateRepositories()
      _ =  logger.info(s"Finished updating Teams and Repos - $count records updated")
    } yield ()
  }

  def reload: Future[Unit] =
    mongoLocks.dataReloadLock
      .withLock {
        logger.info(s"Starting mongo update")
        persistingService.updateRepositories()
      }
      .map(_.getOrElse(sys.error(s"Mongo is locked for ${mongoLocks.dataReloadLock.lockId}")))
      .map { _ =>
        logger.info(s"mongo update completed")
      }
}
