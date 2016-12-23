package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import play.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class DataReloadScheduler @Inject()(actorSystem: ActorSystem,
                                    applicationLifecycle: ApplicationLifecycle,
                                    githubCompositeDataSource: GitCompositeDataSource,
                                    cacheConfig: CacheConfig,
                                    mongoLock: MongoLock
                                   )(implicit ec: ExecutionContext) {

  private val cacheDuration = cacheConfig.teamsCacheDuration


  private val scheduledReload = actorSystem.scheduler.schedule(cacheDuration, cacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    reload.andThen {
      case Success(teamRepositoriesFromGh: Seq[TeamRepositories]) =>
        removeDeletedTeams(teamRepositoriesFromGh)
      case Failure(t) => throw new RuntimeException("Failed to reload and persist teams and repository data from gitub", t)
    }
  }

  applicationLifecycle.addStopHook(() => Future(scheduledReload.cancel()))

  //!@ extract a function and reduce duplication
  def reload: Future[Seq[TeamRepositories]] = {
    mongoLock.tryLock {
      Logger.info(s"Starting mongo update")
      githubCompositeDataSource.persistTeamRepoMapping
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo update completed")
      r
    }
  }

  def removeDeletedTeams(teamRepositoriesFromGh: Seq[TeamRepositories]) = {

    mongoLock.tryLock {
      Logger.info(s"Starting mongo clean up to remove these deleted teams:[${teamRepositoriesFromGh.map(_.teamName)}]")
      githubCompositeDataSource.removeOrphanTeamsFromMongo(teamRepositoriesFromGh)
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo cleanup completed")
      r
    }

  }


}
