package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLock
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class DataReloadScheduler @Inject()(
  actorSystem: ActorSystem,
  applicationLifecycle: ApplicationLifecycle,
  persistingService: PersistingService,
  cacheConfig: CacheConfig,
  mongoLock: MongoLock)(implicit ec: ExecutionContext) {

  private val cacheInitialDelay = cacheConfig.teamsCacheInitialDelay
  private val cacheDuration     = cacheConfig.teamsCacheDuration

  import uk.gov.hmrc.teamsandrepositories.controller.BlockingIOExecutionContext._

  private val scheduledReload = actorSystem.scheduler.schedule(cacheInitialDelay, cacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    reload.andThen {
      case Success(teamRepositoriesFromGh: Seq[TeamRepositories]) =>
        removeDeletedTeams(teamRepositoriesFromGh)
      case Failure(t) =>
        throw new RuntimeException("Failed to reload and persist teams and repository data from gitub", t)
    }
  }

  applicationLifecycle.addStopHook(() => Future(scheduledReload.cancel()))

  def reload: Future[Seq[TeamRepositories]] =
    mongoLock.tryLock {
      Logger.info(s"Starting mongo update")
      persistingService.persistTeamRepoMapping
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo update completed")
      r
    }

  def removeDeletedTeams(teamRepositoriesFromGh: Seq[TeamRepositories]) =
    mongoLock.tryLock {
      Logger.debug(s"Starting mongo clean up (removing orphan teams)")
      persistingService.removeOrphanTeamsFromMongo(teamRepositoriesFromGh)
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo cleanup completed")
      r
    }

}
