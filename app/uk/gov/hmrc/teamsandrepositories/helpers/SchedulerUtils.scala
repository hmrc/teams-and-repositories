package uk.gov.hmrc.teamsandrepositories.helpers

import akka.actor.ActorSystem
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfig
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLock

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait SchedulerUtils {
  def schedule(
              label          : String,
              schedulerConfig: SchedulerConfig
              )(f: => Future[Unit]
  )(implicit actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle, ec: ExecutionContext): Unit =
    if (schedulerConfig.enabled) {
      val initialDelay = schedulerConfig.initialDelay()
      val frequency    = schedulerConfig.frequency()
      Logger.info(s"Enabling $label scheduler, running every $frequency (after initial delay $initialDelay)")
      val cancellable =
        actorSystem.scheduler.schedule(initialDelay, frequency) {
          Logger.info(s"Running $label scheduler")
          f.recover {
            case e => Logger.error(s"$label interrupted because: ${e.getMessage}", e)
          }
        }
      applicationLifecycle.addStopHook(() => Future(cancellable.cancel()))
    } else {
      Logger.info(s"$label scheduler is DISABLED. to enable, configure configure ${schedulerConfig.enabledKey}=true in config.")
    }

  def scheduleWithLock(
                        label            : String
                        , schedulerConfig: SchedulerConfig
                        , lock           : MongoLock
                      )(f: => Future[Unit]
                      )(implicit actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle, ec: ExecutionContext): Unit =
    schedule(label, schedulerConfig) {
      lock.tryLock(f).map {
        case Some(_) => Logger.debug(s"$label finished - releasing lock")
        case None    => Logger.debug(s"$label cannot run - lock ${lock.lockId} is taken... skipping update")
      }.recover {
        case NonFatal(e) => Logger.error(s"$label interrupted because: ${e.getMessage}", e)
      }
    }
}

object SchedulerUtils extends SchedulerUtils

