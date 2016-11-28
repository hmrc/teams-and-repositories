package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import com.google.inject.{Inject, Singleton}
import play.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}


trait CachedRepositoryDataSource[T] {
  def getCachedTeamRepoMapping: Future[CachedResult[T]]

  def reload(): Unit
}

@Singleton
class MemoryCachedRepositoryDataSource[T] @Inject()(dataGetter: DataGetter[T],
                                                    timeStamp: () => LocalDateTime) /*  extends CachedRepositoryDataSource[T] */ {

  private var cachedData: Option[CachedResult[Seq[T]]] = None
  private val initialPromise = Promise[CachedResult[Seq[T]]]()

  import ExecutionContext.Implicits._

  fetchData()

  private def fromSource() =
    dataGetter.runner().map { d => {
      val stamp = timeStamp()
      Logger.debug(s"Cache reloaded at $stamp")
      new CachedResult(d, stamp)
    }
    }

  def getCachedTeamRepoMapping: Future[CachedResult[Seq[T]]] = {
    Logger.info(s"cachedData is available = ${cachedData.isDefined}")
    if (cachedData.isEmpty && initialPromise.isCompleted) {
      Logger.warn("in unexpected state where initial promise is complete but there is not cached data. Perform manual reload.")
    }
    cachedData.fold(initialPromise.future)(d => Future.successful(d))
  }

  def reload() = {
    Logger.info(s"Manual teams repository cache reload triggered")
    fetchData()
  }


  private def fetchData() {

    fromSource().onComplete {
      case Failure(e) => Logger.warn(s"failed to get latest data due to ${e.getMessage}", e)
      case Success(d: CachedResult[Seq[T]]) => {
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

