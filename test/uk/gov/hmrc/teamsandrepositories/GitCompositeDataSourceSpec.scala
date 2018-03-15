package uk.gov.hmrc.teamsandrepositories

import com.codahale.metrics.{Counter, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import java.util.Date

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.{Application, Configuration}
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.Future
import uk.gov.hmrc.githubclient.{GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.services.{GitCompositeDataSource, GithubApiClientDecorator, GithubV3RepositoryDataSource, Timestamper}

class GitCompositeDataSourceSpec
    extends FunSpec
    with Matchers
    with MockitoSugar
    with LoneElement
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with OneAppPerSuite {

  private val githubConfig          = mock[GithubConfig]
  private val persister             = mock[TeamsAndReposPersister]
  private val connector             = mock[MongoConnector]
  private val githubClientDecorator = mock[GithubApiClientDecorator]

  val mockMetrics  = mock[Metrics]
  val mockRegistry = mock[MetricRegistry]
  val mockCounter  = mock[Counter]

  when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
  when(mockRegistry.counter(ArgumentMatchers.any())).thenReturn(mockCounter)

  val now = new Date().getTime
  val testTimestamper = new Timestamper {
    override def timestampF() = now
  }

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .build()

  override protected def beforeEach() = {
    reset(githubConfig)
    reset(persister)
    reset(connector)
    reset(githubClientDecorator)
  }

  describe("buildDataSource") {
    it("should create the right CompositeRepositoryDataSource") {

      val gitApiOpenConfig = mock[GitApiConfig]

      when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

      val openUrl = "open.com"
      val openKey = "open.key"
      when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
      when(gitApiOpenConfig.key).thenReturn(openKey)

      val openGithubClient = mock[GithubApiClient]
      when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

      val compositeRepositoryDataSource = new GitCompositeDataSource(
        githubConfig,
        persister,
        connector,
        githubClientDecorator,
        testTimestamper,
        mock[Metrics],
        Configuration())

      verify(gitApiOpenConfig).apiUrl
      verify(gitApiOpenConfig).key

      compositeRepositoryDataSource.dataSource shouldBe compositeRepositoryDataSource.openTeamsRepositoryDataSource

    }
  }

}
