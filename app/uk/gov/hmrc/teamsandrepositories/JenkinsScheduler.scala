package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import reactivemongo.api.commands.UpdateWriteResult
import uk.gov.hmrc.teamsandrepositories.config.JenkinsConfig
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLock
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JenkinsScheduler @Inject()(
                                  actorSystem: ActorSystem,
                                  applicationLifecycle: ApplicationLifecycle,
                                  jenkinsService: JenkinsService,
                                  config: JenkinsConfig,
                                  mongoLock: MongoLock
                                )(implicit ec: ExecutionContext) {

  private val scheduledReload = actorSystem.scheduler.schedule(config.initialDelay, config.reloadDuration) {
    if (config.reloadEnabled) {
      Logger.info("Scheduled update of jenkins links triggered")
      reload.recover {
        case ex: Throwable => Logger.error("Failed to update jenkins links", ex)
      }
    }
    else {
      Logger.info("Jenkins reload scheduler is disabled. You can enable it by setting jenkins.reloadEnabled=true")
    }
  }

  private def reload: Future[Seq[UpdateWriteResult]] = {
    mongoLock.tryLock {
      Logger.info("Starting mongo update")
      jenkinsService.updateBuildJobs()
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info("Mongo update complete")
      r
    }

  }
}
