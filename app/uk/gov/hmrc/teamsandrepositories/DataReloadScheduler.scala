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

package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DataReloadScheduler @Inject()(
     persistingService: PersistingService
   , config           : SchedulerConfigs
   , mongoLocks       : MongoLocks
   )( implicit
      actorSystem         : ActorSystem
    , applicationLifecycle: ApplicationLifecycle
    ) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val logger = Logger(this.getClass)

  import ExecutionContext.Implicits.global

  scheduleWithLock("Teams and Repos Reloader", config.dataReloadScheduler, mongoLocks.dataReloadLock) {
    for {
      teamRepositoriesFromGh <- persistingService.persistTeamRepoMapping
      _ =  logger.info("Finished updating Teams and Repos - Now removing orphan Teams")
      _ <- persistingService.removeOrphanTeamsFromMongo(teamRepositoriesFromGh)
      _ =  logger.info("Finished removing orphan Teams")
    } yield ()
  }

  def reload: Future[Seq[TeamRepositories]] =
    mongoLocks.dataReloadLock.tryLock {
      logger.info(s"Starting mongo update")
      persistingService.persistTeamRepoMapping
    }.map(_.getOrElse(sys.error(s"Mongo is locked for ${mongoLocks.dataReloadLock.lockId}")))
     .map { r =>
      logger.info(s"mongo update completed")
      r
    }
}
