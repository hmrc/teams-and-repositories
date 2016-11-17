package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import akka.actor.ActorSystem
import play.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.Source
import scala.util.{Failure, Success, Try}

trait CachedRepositoryDataSource[T] {
  def getCachedTeamRepoMapping: Future[CachedResult[T]]
  def reload(): Unit
}

class MemoryCachedRepositoryDataSource[T](akkaSystem: ActorSystem,
                                          cacheConfig: CacheConfig,
                                          dataSource: () => Future[T],
                                          timeStamp: () => LocalDateTime) extends CachedRepositoryDataSource[T] {

  private var cachedData: Option[CachedResult[T]] = None
  private val initialPromise = Promise[CachedResult[T]]()

  import ExecutionContext.Implicits._

  dataUpdate()

  private def fromSource =
    dataSource().map { d => {
      val stamp = timeStamp()
      Logger.debug(s"Cache reloaded at $stamp")
      new CachedResult(d, stamp) }}

  def getCachedTeamRepoMapping: Future[CachedResult[T]] = {
    Logger.info(s"cachedData is available = ${cachedData.isDefined}")
    if (cachedData.isEmpty && initialPromise.isCompleted) {
      Logger.warn("in unexpected state where initial promise is complete but there is not cached data. Perform manual reload.")
    }
    cachedData.fold(initialPromise.future)(d => Future.successful(d))
  }

  def reload() = {
    Logger.info(s"Manual teams repository cache reload triggered")
    dataUpdate()
  }

  Logger.info(s"Initialising cache reload every ${cacheConfig.teamsCacheDuration}")
  akkaSystem.scheduler.schedule(cacheConfig.teamsCacheDuration, cacheConfig.teamsCacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    dataUpdate()
  }

  private def dataUpdate() {

    fromSource.onComplete {
      case Failure(e) => Logger.warn(s"failed to get latest data due to ${e.getMessage}", e)
      case Success(d) => {
        synchronized {
          this.cachedData = Some(d)
          Logger.info(s"data update completed successfully")

          if (!initialPromise.isCompleted) {
            Logger.debug("early clients being sent result")
            this.initialPromise.success(d)
          }
        }
      }
    }
  }
}

class FileCachedRepositoryDataSource(cacheFilename: String) extends CachedRepositoryDataSource[Seq[TeamRepositories]] {

  implicit val repositoryFormats = Json.format[Repository]
  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  private var cachedData = loadCacheData

  private def loadCacheData: Option[CachedResult[Seq[TeamRepositories]]] = {
    Try(Json.parse(Source.fromFile(cacheFilename).mkString)
      .as[Seq[TeamRepositories]]) match {
      case Success(repos) => Some(new CachedResult(repos, LocalDateTime.now))
      case Failure(e) =>
        e.printStackTrace()
        None
    }
  }

  override def getCachedTeamRepoMapping: Future[CachedResult[Seq[TeamRepositories]]] = {
    Future.successful(cachedData.get)
  }

  override def reload(): Unit = cachedData = loadCacheData
}