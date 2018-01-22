package uk.gov.hmrc.teamsandrepositories

import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.time.{Duration, LocalDateTime}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.githubclient.APIRateLimitExceededException
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLock
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services.GitCompositeDataSource

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
@Singleton
class DataReloadScheduler @Inject()(
  actorSystem: ActorSystem,
  applicationLifecycle: ApplicationLifecycle,
  githubCompositeDataSource: GitCompositeDataSource,
  cacheConfig: CacheConfig,
  mongoLock: MongoLock)(implicit ec: ExecutionContext) {

  private val cacheDuration = cacheConfig.teamsCacheDuration

  private val nightlyInitialDelay: FiniteDuration          = cacheConfig.nightlyInitialDelay
  private val retryDelayBetweenFullRefresh: FiniteDuration = cacheConfig.retryDelayBetweenFullRefresh

  private val twentyFourHours = FiniteDuration(24, TimeUnit.HOURS)

  import BlockingIOExecutionContext._

  private val scheduledReload = actorSystem.scheduler.schedule(cacheDuration, cacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    reload(false).andThen {
      case Success(teamRepositoriesFromGh: Seq[TeamRepositories]) =>
        removeDeletedTeams(teamRepositoriesFromGh)
      case Failure(t) =>
        throw new RuntimeException("Failed to reload and persist teams and repository data from gitub", t)
    }
  }

  private val scheduledNightlyFullRefresh = {
    Logger.warn(
      s"Scheduling full refresh for ~${nightlyInitialDelay.toMinutes / 60.0} hours from now @${LocalDateTime.now().plus(nightlyInitialDelay.toMillis, ChronoUnit.MILLIS)}")
    actorSystem.scheduler.schedule(nightlyInitialDelay, twentyFourHours) {

      Logger.info("Scheduled FULL teams cache reload triggered")

      def runFullRefresh: Future[Seq[TeamRepositories]] = {
        Logger.info("running the full refresh.....")
        reload(true).andThen {
          case Success(teamRepositoriesFromGh: Seq[TeamRepositories]) =>
            removeDeletedTeams(teamRepositoriesFromGh)
          case Failure(t) =>
            if (t.isInstanceOf[APIRateLimitExceededException]) {
              Logger.error(
                s"full refresh failed, rescheduling for ${retryDelayBetweenFullRefresh.toMinutes} mins from now @${LocalDateTime
                  .now()
                  .plus(retryDelayBetweenFullRefresh.toMillis, ChronoUnit.MILLIS)}.....",
                t
              )
              val scheduledRetry = actorSystem.scheduler.scheduleOnce(retryDelayBetweenFullRefresh) {
                runFullRefresh
              }
              applicationLifecycle.addStopHook(() => Future(scheduledRetry.cancel()))
            } else
              throw new RuntimeException("Failed to perform a full refresh of teams and repository data", t)
        }
      }

      runFullRefresh
    }
  }

  applicationLifecycle.addStopHook(() => Future(scheduledReload.cancel()))
  applicationLifecycle.addStopHook(() => Future(scheduledNightlyFullRefresh.cancel()))

  def reload(fullRefreshWithHighApiCall: Boolean): Future[Seq[TeamRepositories]] =
    mongoLock.tryLock {
      Logger.info(s"Starting mongo update")
      githubCompositeDataSource.persistTeamRepoMapping(fullRefreshWithHighApiCall)
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo update completed")
      r
    }

  def removeDeletedTeams(teamRepositoriesFromGh: Seq[TeamRepositories]) =
    mongoLock.tryLock {
      Logger.debug(s"Starting mongo clean up (removing orphan teams)")
      githubCompositeDataSource.removeOrphanTeamsFromMongo(teamRepositoriesFromGh)
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo cleanup completed")
      r
    }

}
