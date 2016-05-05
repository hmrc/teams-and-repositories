package uk.gov.hmrc.catalogue.teams

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsArray
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.catalogue.CachedList
import uk.gov.hmrc.catalogue.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, TeamRepositories}

import scala.concurrent.Future

class TeamsRepositoryControllerSpec extends PlaySpec with MockitoSugar with Results {

  trait FakeConfig extends UrlTemplatesProvider {
    val ciUrlTemplates  = new UrlTemplates(
      Seq(new UrlTemplate("open","$name")),
      Seq(new UrlTemplate("closed","$name")))
  }

  val timestamp = new DateTime(2016, 4, 5, 12, 57)
  val data = new CachedList[TeamRepositories](
    Seq(new TeamRepositories("test-team", List(Repository("repo-name", "repo-url")))),
    timestamp)

  "Retrieving a list of teams and repositories" should {

    "Return a json representation of the data, including the cache timestamp" in {

      val fakeDataSource = mock[CachingTeamsRepositoryDataSource]
      when(fakeDataSource.getTeamRepoMapping).thenReturn(Future.successful(data))

      val controller = new TeamsRepositoryController with FakeConfig {
        override def dataSource: CachingTeamsRepositoryDataSource = fakeDataSource
      }

      val result = controller.teamRepository().apply(FakeRequest())

      val json = contentAsJson(result)
      val team = (json \ "data").as[JsArray].value.head
      val repository = (team \ "repositories").as[JsArray].value.head

      (json \ "cacheTimestamp").as[DateTime] mustBe timestamp
      (team \ "teamName").as[String] mustBe "test-team"
      (repository \ "name").as[String] mustBe "repo-name"
      (repository \ "url").as[String] mustBe "repo-url"
      (repository \ "isInternal").as[Boolean] mustBe false
      (repository \ "isMicroservice").as[Boolean] mustBe false

    }
  }
}
