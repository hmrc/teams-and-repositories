package uk.gov.hmrc.teamsandrepositories

import java.util.Date

import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.githubclient.{GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services.{GitCompositeDataSource, GithubApiClientDecorator, GithubV3RepositoryDataSource, Timestamper}

import scala.concurrent.Future
import scala.concurrent.Future.successful

class GitCompositeDataSourceSpec extends FunSpec with Matchers with MockitoSugar with LoneElement with ScalaFutures with OptionValues with BeforeAndAfterEach with OneAppPerSuite {

  private val githubConfig = mock[GithubConfig]
  private val persister = mock[TeamsAndReposPersister]
  private val connector = mock[MongoConnector]
  private val githubClientDecorator = mock[GithubApiClientDecorator]

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
      val gitApiEnterpriseConfig = mock[GitApiConfig]

      when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
      when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

      val enterpriseUrl = "enterprise.com"
      val enterpriseKey = "enterprise.key"
      when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
      when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

      val openUrl = "open.com"
      val openKey = "open.key"
      when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
      when(gitApiOpenConfig.key).thenReturn(openKey)

      val enterpriseGithubClient = mock[GithubApiClient]
      val openGithubClient = mock[GithubApiClient]
      when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
      when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

      val compositeRepositoryDataSource = new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator, testTimestamper)

      verify(gitApiOpenConfig).apiUrl
      verify(gitApiOpenConfig).key
      verify(gitApiEnterpriseConfig).apiUrl
      verify(gitApiEnterpriseConfig).key

      compositeRepositoryDataSource.dataSources.size shouldBe 2

      val enterpriseDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(0)
      enterpriseDataSource shouldBe compositeRepositoryDataSource.enterpriseTeamsRepositoryDataSource

      val openDataSource: GithubV3RepositoryDataSource = compositeRepositoryDataSource.dataSources(1)
      openDataSource shouldBe compositeRepositoryDataSource.openTeamsRepositoryDataSource
    }
  }




  describe("Retrieving team repo mappings") {

    it("return the combination of all input sources") {

      val teamsList1 = List(
        TeamRepositories("A", List(GitRepository("A_r", "Some Description", "url_A", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()))

      val teamsList2 = List(
        TeamRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()),
        TeamRepositories("E", List(GitRepository("E_r", "Some Description", "url_E", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()),
        TeamRepositories("F", List(GitRepository("F_r", "Some Description", "url_F", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()))

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(successful(teamsList1))

      val dataSource2 = mock[GithubV3RepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(successful(teamsList2))

      val compositeDataSource = buildCompositeDataSource(List(dataSource1, dataSource2))
      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      result.length shouldBe 6
      result should contain(teamsList1.head)
      result should contain(teamsList1(1))
      result should contain(teamsList1(2))
      result should contain(teamsList2.head)
      result should contain(teamsList2(1))
      result should contain(teamsList2(2))
    }

    it("combine teams that have the same names in both sources and sort repositories alphabetically") {

      val repoAA = GitRepository("A_A", "Some Description", "url_A_A", now, now, updateDate = testTimestamper.timestampF())
      val repoAB = GitRepository("A_B", "Some Description", "url_A_B", now, now, updateDate = testTimestamper.timestampF())
      val repoAC = GitRepository("A_C", "Some Description", "url_A_C", now, now, updateDate = testTimestamper.timestampF())

      val teamsList1 = List(
        TeamRepositories("A", List(repoAC, repoAB), testTimestamper.timestampF()),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()))

      val teamsList2 = List(
        TeamRepositories("A", List(repoAA), testTimestamper.timestampF()),
        TeamRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now, updateDate = testTimestamper.timestampF())), testTimestamper.timestampF()))

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(successful(teamsList1))

      val dataSource2 = mock[GithubV3RepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(successful(teamsList2))

      val compositeDataSource = buildCompositeDataSource(List(dataSource1, dataSource2))

      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      result.length shouldBe 4
      result.find(_.teamName == "A").get.repositories should contain inOrderOnly(
        repoAA, repoAB, repoAC)

      result should contain(teamsList1(1))
      result should contain(teamsList1(2))
      result should contain(teamsList2(1))

    }

    it("should remove deleted teams") {
      val dataSource1 = mock[GithubV3RepositoryDataSource]
      val dataSource2 = mock[GithubV3RepositoryDataSource]

      val compositeDataSource = buildCompositeDataSource(List(dataSource1, dataSource2))

      val teamRepositoriesInMongo = Seq(
        TeamRepositories("team-a", Nil, System.currentTimeMillis()),
        TeamRepositories("team-b", Nil, System.currentTimeMillis()),
        TeamRepositories("team-c", Nil, System.currentTimeMillis()),
        TeamRepositories("team-d", Nil, System.currentTimeMillis())
      )

      when(persister.getAllTeamAndRepos).thenReturn(Future.successful(teamRepositoriesInMongo, None))
      when(persister.deleteTeams(ArgumentMatchers.any())).thenReturn(Future.successful(Set("something not important")))

      compositeDataSource.removeOrphanTeamsFromMongo(Seq(TeamRepositories("team-a", Nil, System.currentTimeMillis()), TeamRepositories("team-c", Nil, System.currentTimeMillis())))

      verify(persister, Mockito.timeout(1000)).deleteTeams(Set("team-b", "team-d"))
    }

    it("should update the timestamp") {

      val teamsList = List(
        TeamRepositories("A", List(GitRepository("A_r", "Some Description", "url_A", now, now, updateDate = System.currentTimeMillis())), System.currentTimeMillis()),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now, updateDate = System.currentTimeMillis())), System.currentTimeMillis()),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now, updateDate = System.currentTimeMillis())), System.currentTimeMillis()))

      val dataSource = mock[GithubV3RepositoryDataSource]
      when(dataSource.getTeamRepoMapping).thenReturn(successful(teamsList))


      val compositeDataSource = buildCompositeDataSource(List(dataSource))
      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      verify(persister, Mockito.timeout(1000)).updateTimestamp(ArgumentMatchers.any())
    }

  }

  private def buildCompositeDataSource(dataSourceList: List[GithubV3RepositoryDataSource]) = {

    val gitApiOpenConfig = mock[GitApiConfig]
    val gitApiEnterpriseConfig = mock[GitApiConfig]

    when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
    when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

    val enterpriseUrl = "enterprise.com"
    val enterpriseKey = "enterprise.key"
    when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
    when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

    val openUrl = "open.com"
    val openKey = "open.key"
    when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
    when(gitApiOpenConfig.key).thenReturn(openKey)

    val enterpriseGithubClient = mock[GithubApiClient]
    val openGithubClient = mock[GithubApiClient]
    when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
    when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)


    when(persister.update(ArgumentMatchers.any())).thenAnswer(new Answer[Future[TeamRepositories]] {
      override def answer(invocation: InvocationOnMock): Future[TeamRepositories] = {
        val args = invocation.getArguments()
        Future.successful(args(0).asInstanceOf[TeamRepositories])
      }
    })

    when(persister.updateTimestamp(ArgumentMatchers.any())).thenReturn(Future.successful(true))

    new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator, testTimestamper) {
      override val dataSources: List[GithubV3RepositoryDataSource] = dataSourceList
    }
  }
}
