package uk.gov.hmrc.teamsandrepositories

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.ApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, MongoLock}
import uk.gov.hmrc.teamsandrepositories.services.PersistingService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class DataReloadSchedulerSpec
    extends PlaySpec
    with MockitoSugar
    with Results
    with OptionValues
    with GuiceOneServerPerSuite
    with Eventually
    with BeforeAndAfterAll {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .build()

  val testMongoLock = new MongoLock(mock[MongoConnector]) {
    override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
      body.map(t => Some(t))
  }

  "reload the cache and remove orphan teams at the configured intervals" in {

    val mockCacheConfig            = mock[CacheConfig]
    val mockGitCompositeDataSource = mock[PersistingService]

    when(mockGitCompositeDataSource.persistTeamRepoMapping(any())).thenReturn(Future(Nil))
    when(mockGitCompositeDataSource.removeOrphanTeamsFromMongo(any())(any())).thenReturn(Future(Set.empty[String]))

    when(mockCacheConfig.teamsCacheInitialDelay).thenReturn(100 millisecond)
    when(mockCacheConfig.teamsCacheDuration).thenReturn(100 millisecond)

    new DataReloadScheduler(
      actorSystem          = app.actorSystem,
      applicationLifecycle = app.injector.instanceOf[ApplicationLifecycle],
      persistingService    = mockGitCompositeDataSource,
      cacheConfig          = mockCacheConfig,
      mongoLock            = testMongoLock
    )

    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping(any())
    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).removeOrphanTeamsFromMongo(any())(any())
  }
}
