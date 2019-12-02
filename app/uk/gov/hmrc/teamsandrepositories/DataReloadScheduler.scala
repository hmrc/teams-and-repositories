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
                                     persistingService: PersistingService,
                                     config: SchedulerConfigs,
                                     mongoLocks: MongoLocks)(implicit actorSystem: ActorSystem,
                        applicationLifecycle: ApplicationLifecycle) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  import ExecutionContext.Implicits.global

  scheduleWithLock("Teams and Repos Reloader", config.dataReloadScheduler, mongoLocks.dataReloadLock) {
    for {
      teamRepositoriesFromGh <- persistingService.persistTeamRepoMapping
      _ =  Logger.info("Finished updating Teams and Repos - Now removing orphan Teams")
      _ <- persistingService.removeOrphanTeamsFromMongo(teamRepositoriesFromGh)
      _ =  Logger.info("Finished removing orphan Teams")
    } yield ()
  }

  def reload: Future[Seq[TeamRepositories]] =
    mongoLocks.dataReloadLock.tryLock {
      Logger.info(s"Starting mongo update")
      persistingService.persistTeamRepoMapping
    } map {
      _.getOrElse(throw new RuntimeException(s"Mongo is locked for ${mongoLocks.dataReloadLock.lockId}"))
    } map { r =>
      Logger.info(s"mongo update completed")
      r
    }

}
