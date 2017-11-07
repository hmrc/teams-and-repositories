package uk.gov.hmrc.teamsandrepositories


import com.google.inject.{Injector, Key, TypeLiteral}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, MongoLock}
import uk.gov.hmrc.teamsandrepositories.services.{GitCompositeDataSource, Timestamper}


import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ModuleSpec extends PlaySpec with MockitoSugar with Results with OptionValues with OneServerPerSuite with Eventually {

  val testMongoLock = new MongoLock(mock[MongoConnector]) {
    override def tryLock[T](body: => Future[T])(implicit ec: ExecutionContext): Future[Option[T]] =
      body.map(Some(_))
  }

  val testTimestamper = new Timestamper

  val mockCacheConfig = mock[CacheConfig]
  val intervalDuration = 100 millisecond

  when(mockCacheConfig.teamsCacheDuration).thenReturn(intervalDuration)
  when(mockCacheConfig.nightlyInitialDelay).thenReturn(1 hour)

  val mockGitCompositeDataSource = mock[GitCompositeDataSource]
  when(mockGitCompositeDataSource.persistTeamRepoMapping(any())(any())).thenReturn(Future.successful(Nil))
  when(mockGitCompositeDataSource.removeOrphanTeamsFromMongo(any())(any())).thenReturn(Future.successful(Set.empty[String]))

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .overrides(
        bind[CacheConfig].toInstance(mockCacheConfig),
        bind[Timestamper].toInstance(testTimestamper),
        bind[GitCompositeDataSource].toInstance(mockGitCompositeDataSource),
        bind[MongoLock].toInstance(testMongoLock)
      )
      .overrides(new Module())
      .build()


  "load DataReloadScheduler eagerly which should result in reload of the cache at the configured intervals" in {

    val guiceInjector = app.injector.instanceOf(classOf[Injector])

    val key = Key.get(new TypeLiteral[DataReloadScheduler]() {})

    guiceInjector.getInstance(key).isInstanceOf[DataReloadScheduler] mustBe true
    verify(mockGitCompositeDataSource, Mockito.timeout(500).atLeast(2)).persistTeamRepoMapping(any())(any())

  }
}


