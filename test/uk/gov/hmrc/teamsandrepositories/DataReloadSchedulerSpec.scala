package uk.gov.hmrc.teamsandrepositories

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.inject.ApplicationLifecycle
import play.api.mvc.Results
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class DataReloadSchedulerSpec extends PlaySpec with MockitoSugar with Results with OptionValues with OneServerPerSuite with Eventually {

  val mockCacheConfig = mock[CacheConfig]
  val mockGitCompositeDataSource = mock[GitCompositeDataSource]

  when(mockGitCompositeDataSource.persistTeamRepoMapping).thenReturn(Future(Nil))
  when(mockGitCompositeDataSource.removeOrphanTeamsFromMongo(any())).thenReturn(Future(Set.empty[String]))

  when(mockCacheConfig.teamsCacheDuration).thenReturn(100 millisecond)

  val testMongoLock = new MongoLock(mock[MongoConnector]) {
    override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
      body.map(Some(_))
  }

  "reload the cache and remove orphan teams at the configured intervals" in {

    val testScheduler =
      new DataReloadScheduler(actorSystem = app.actorSystem,
        applicationLifecycle = app.injector.instanceOf[ApplicationLifecycle],
        githubCompositeDataSource = mockGitCompositeDataSource,
        cacheConfig = mockCacheConfig,
        mongoLock = testMongoLock)

    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping
    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).removeOrphanTeamsFromMongo(any())
  }
}
