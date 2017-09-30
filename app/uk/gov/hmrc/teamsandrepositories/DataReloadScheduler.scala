package uk.gov.hmrc.teamsandrepositories

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLock
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services.GitCompositeDataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class DataReloadScheduler @Inject()(actorSystem: ActorSystem,
                                    applicationLifecycle: ApplicationLifecycle,
                                    githubCompositeDataSource: GitCompositeDataSource,
                                    cacheConfig: CacheConfig,
                                    mongoLock: MongoLock)(implicit ec: ExecutionContext) {

  private val cacheDuration = cacheConfig.teamsCacheDuration

  lazy val logger = LoggerFactory.getLogger(this.getClass)

  import BlockingIOExecutionContext._


  private val scheduledReload = actorSystem.scheduler.schedule(cacheDuration, cacheDuration) {
    logger.info("Scheduled teams repository cache reload triggered")
    reload.andThen {
      case Success(teamRepositoriesFromGh: Seq[TeamRepositories]) =>
        removeDeletedTeams(teamRepositoriesFromGh)
      case Failure(t) => throw new RuntimeException("Failed to reload and persist teams and repository data from gitub", t)
    }
  }

  applicationLifecycle.addStopHook(() => Future(scheduledReload.cancel()))


  def reload: Future[Seq[TeamRepositories]] = {
    mongoLock.tryLock {
      logger.debug(s"Starting mongo update")
      githubCompositeDataSource.persistTeamRepoMapping
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      logger.debug(s"mongo update completed")
      r
    }
  }

  def removeDeletedTeams(teamRepositoriesFromGh: Seq[TeamRepositories]) = {

    mongoLock.tryLock {
      logger.debug(s"Starting mongo clean up (removing orphan teams)")
      githubCompositeDataSource.removeOrphanTeamsFromMongo(teamRepositoriesFromGh)
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLock.lockId}"))
    } map { r =>
      logger.debug(s"mongo cleanup completed")
      r
    }

  }


}
