package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import scala.concurrent.ExecutionContext

@Singleton
class JenkinsScheduler @Inject()(jenkinsService: JenkinsService,
                                 config: SchedulerConfigs,
                                 mongoLocks: MongoLocks)(
                                 implicit actorSystem: ActorSystem,
                                 applicationLifecycle: ApplicationLifecycle)
extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  import ExecutionContext.Implicits.global

  scheduleWithLock("Jenkins Reloader", config.jenkinsScheduler, mongoLocks.jenkinsLock) {
    for {
      _ <- jenkinsService.updateBuildJobs()
      _ =  Logger.info("Finished updating Build Jobs")
    } yield ()
  }

}
