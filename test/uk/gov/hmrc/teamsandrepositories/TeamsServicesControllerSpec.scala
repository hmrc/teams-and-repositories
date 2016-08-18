/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.mvc.{AnyContentAsEmpty, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.Repository
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates}

import scala.concurrent.Future

class TeamsServicesControllerSpec extends PlaySpec with MockitoSugar with Results with OptionValues {

  val timestamp = LocalDateTime.of(2016, 4, 5, 12, 57, 10)

  def controllerWithData(data: CachedResult[Seq[TeamRepositories]]) = {
    val fakeDataSource = mock[CachingRepositoryDataSource[Seq[TeamRepositories]]]
    when(fakeDataSource.getCachedTeamRepoMapping).thenReturn(Future.successful(data))

    new TeamsServicesController {
      override def dataSource = fakeDataSource

      override def ciUrlTemplates = new UrlTemplates(
        Seq(new UrlTemplate("closed", "closed", "$name")),
        Seq(new UrlTemplate("open", "open", "$name")),
        Map(
          "env1" -> Seq(
            new UrlTemplate("log1", "log 1", "$name"),
            new UrlTemplate("mon1", "mon 1", "$name")),
          "env2" -> Seq(
            new UrlTemplate("log1", "log 1", "$name"))
        ))
    }
  }

  val defaultData = new CachedResult[Seq[TeamRepositories]](
    Seq(
      new TeamRepositories("test-team", List(
        Repository("repo-name", "repo-url", repoType = RepoType.Deployable),
        Repository("library-repo", "library-url", repoType = RepoType.Library)

      )),

      new TeamRepositories("another-team", List(
        Repository("another-repo", "another-url", repoType = RepoType.Deployable),
        Repository("middle-repo", "middle-url", repoType = RepoType.Deployable),
        Repository("alibrary-repo", "library-url", repoType = RepoType.Library)
      ))
    ),
    timestamp)

  def singleRepoResult(teamName: String = "test-team", repoName: String = "repo-name", repoUrl: String = "repo-url", isInternal: Boolean = true) = {
    new CachedResult[Seq[TeamRepositories]](Seq(
      new TeamRepositories("test-team", List(
        Repository(repoName, repoUrl, repoType = RepoType.Deployable, isInternal = isInternal)))), timestamp)
  }


  "Teams controller" should {

    "have the correct url set up for the teams list" in {
      uk.gov.hmrc.teamsandrepositories.routes.TeamsServicesController.teams().url mustBe "/api/teams"
    }

    "have the correct url set up for a team's services" in {
      uk.gov.hmrc.teamsandrepositories.routes.TeamsServicesController.team("test-team").url mustBe "/api/teams/test-team"
    }

    "have the correct url set up for the list of all services" in {
      uk.gov.hmrc.teamsandrepositories.routes.TeamsServicesController.services().url mustBe "/api/services"
    }

  }

  "Retrieving a list of teams" should {

    "Return a json representation of the data, including the cache timestamp" in {
      val controller = controllerWithData(defaultData)
      val result = controller.teams().apply(FakeRequest())

      val timestampHeader = header("x-cache-timestamp", result)
      val team = contentAsJson(result).as[JsArray].value.head

      timestampHeader.value mustBe "Tue, 5 Apr 2016 12:57:10 GMT"
      team.as[String] mustBe "test-team"
    }
  }

  "Retrieving a list of repositories for a team" should {

    "Return all repo types belonging to a team" in {
      val controller = controllerWithData(defaultData)
      val result = controller.team("another-team").apply(FakeRequest())

      val timestampHeader = header("x-cache-timestamp", result)
      val data = contentAsJson(result).as[Map[String, List[String]]]

      data.size mustBe 3
      data mustBe Map(
        "Deployable" -> List("another-repo", "middle-repo"),
        "Library" -> List("alibrary-repo"),
        "Other" -> List()
      )
    }

    "Return information about all the teams that have access to a repo" in {
      val sourceData = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(Repository("repo-name", "repo-url", repoType = RepoType.Deployable))),
          new TeamRepositories("another-team", List(Repository("repo-name", "repo-url", repoType = RepoType.Deployable)))
        ),
        timestamp)

      val controller = controllerWithData(sourceData)
      val result = controller.team("another-team").apply(FakeRequest())

      contentAsJson(result).as[Map[String, List[String]]] mustBe Map(
        "Deployable" -> List("repo-name"),
        "Library" -> List(),
        "Other" -> List())
    }

    "not show the same service twice when it has an open and internal source repository" in {
      val sourceData = new CachedResult[Seq[TeamRepositories]](
        Seq(new TeamRepositories("test-team", List(
          Repository("repo-name", "Another-url", repoType = RepoType.Deployable),
          Repository("repo-name", "repo-url", repoType = RepoType.Deployable),
          Repository("aadvark-repo", "aadvark-url", repoType = RepoType.Deployable)))),
        timestamp)

      val controller = controllerWithData(sourceData)
      val result = controller.team("test-team").apply(FakeRequest())

      contentAsJson(result).as[Map[String, List[String]]] mustBe Map(
        "Deployable" -> List("aadvark-repo", "repo-name"),
        "Library" -> List(),
        "Other" -> List()
      )
    }
  }

  "Retrieving a list of all libraries" should {

    "retun a name list of all the libraries" in {
      val controller = controllerWithData(defaultData)
      val result = controller.libraries()(FakeRequest())
      val resultJson = contentAsJson(result)
      val libraryNames = resultJson.as[Seq[String]]
      libraryNames mustBe List("alibrary-repo", "library-repo")
    }

    "Return a json representation of the data when request has a details query parameter" in {
      val controller = controllerWithData(defaultData)

      val result = controller.libraries().apply(FakeRequest("GET", "/libraries?details=true"))

      val resultJson = contentAsJson(result)

      val libraryNames = resultJson.as[Seq[JsObject]].map(_.value("name").as[String])
      libraryNames mustBe List("alibrary-repo", "library-repo")

      val last = resultJson.as[Seq[JsObject]].last

      (last \ "githubUrls").as[JsArray].value.size mustBe 1

      last.nameField mustBe "library-repo"
      last.teamNameSeq mustBe Seq("test-team")

      val ciDetails: Seq[JsValue] = (last \ "ci").as[JsArray].value
      ciDetails.size mustBe 1

      ciDetails(0).as[JsObject].as[Map[String, String]] mustBe Map("name" -> "open", "displayName" -> "open", "url" -> "library-repo")


    }
  }


  "Retrieving a list of all services" should {

    "Return a json representation of the data sorted alphabetically, including the cache timestamp, when the request has a details query parameter" in {
      val controller = controllerWithData(defaultData)

      val result = controller.services().apply(FakeRequest("GET", "/services?details=true"))

      val resultJson = contentAsJson(result)

      val serviceNames = resultJson.as[Seq[JsObject]].map(_.value("name").as[String])
      serviceNames mustBe List("another-repo", "middle-repo", "repo-name")

      val last = resultJson.as[Seq[JsObject]].last

      (last \ "githubUrls").as[JsArray].value.size mustBe 1

      last.nameField mustBe "repo-name"
      last.teamNameSeq mustBe Seq("test-team")

      val environments = (last \ "environments").as[JsArray].value

      val env1Services = environments.find(_ \ "name" == JsString("env1")).value.as[JsObject] \ "services"
      val env1Links = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services = environments.find(_ \ "name" == JsString("env2")).value.as[JsObject] \ "services"
      val env2Links = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))
    }

    "Return a json representation of the data sorted alphabetically, including the cache timestamp, when the request doesn't have a servicedetails content type" in {
      val controller = controllerWithData(defaultData)
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      val result = controller.services().apply(request)

      val serviceList = contentAsJson(result).as[Seq[String]]

      serviceList mustBe Seq("another-repo", "middle-repo", "repo-name")
    }

    "Ignore case when sorting alphabetically" in {
      val sourceData = new CachedResult[Seq[TeamRepositories]](
        Seq(new TeamRepositories("test-team", List(
          Repository("Another-repo", "Another-url", repoType = RepoType.Deployable),
          Repository("repo-name", "repo-url", repoType = RepoType.Deployable),
          Repository("aadvark-repo", "aadvark-url", repoType = RepoType.Deployable)))),
        timestamp)

      val controller = controllerWithData(sourceData)
      val result = controller.services().apply(FakeRequest())

      contentAsJson(result).as[List[String]] mustBe List("aadvark-repo", "Another-repo", "repo-name")
    }

    //TODO this should not be a controller test
    "Flatten team info if a service belongs to multiple teams" in {

      val data = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(Repository("repo-name", "repo-url", repoType = RepoType.Deployable))),
          new TeamRepositories("another-team", List(Repository("repo-name", "repo-url", repoType = RepoType.Deployable)))
        ),
        timestamp)

      val controller = controllerWithData(data)
      val result = controller.services().apply(FakeRequest())

      val json = contentAsJson(result)

      json.as[JsArray].value.size mustBe 1
    }

    //TODO this should not be a controller test
    "Treat as one service if an internal and an open repo exist" in {

      val data = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(
            Repository("repo-name", "repo-url", repoType = RepoType.Deployable, isInternal = true),
            Repository("repo-name", "repo-open-url", repoType = RepoType.Deployable, isInternal = false)))),
        timestamp)

      val controller = controllerWithData(data)
      val result = controller.services().apply(FakeRequest("GET", "/services?details=true"))

      val json = contentAsJson(result)

      val jsonData = json.as[JsArray].value
      jsonData.length mustBe 1

      val first = jsonData.head
      first.nameField mustBe "repo-name"
      first.teamNameSeq mustBe Seq("test-team")

      val githubLinks = (first \ "githubUrls").as[JsArray].value

      githubLinks(0).nameField mustBe "github-enterprise"
      githubLinks(0).urlField mustBe "repo-url"

      githubLinks(1).nameField mustBe "github-com"
      githubLinks(1).urlField mustBe "repo-open-url"

    }

    "return the empty list for repository type if a team does not have it" in {

      val data = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(
            Repository("repo-name", "repo-url", repoType = RepoType.Library, isInternal = true),
            Repository("repo-open-name", "repo-open-url", repoType = RepoType.Library, isInternal = false)))),
        timestamp)

      val controller = controllerWithData(data)
      val result = controller.team("test-team").apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[Map[String, List[String]]]
      jsonData.get("Deployable") mustBe Some(List())

    }

    "Return an empty list if a team has no repositories" in {

      val data = new CachedResult[Seq[TeamRepositories]](Seq(new TeamRepositories("test-team", List())), timestamp)

      val controller = controllerWithData(data)
      val result = controller.team("test-team").apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[Map[String, List[String]]]
      jsonData mustBe Map(
        "Deployable" -> List(),
        "Library" -> List(),
        "Other" -> List()

      )

    }

    "Return a 404 if a team does not exist at all" in {

      val data = new CachedResult[Seq[TeamRepositories]](Seq(), timestamp)

      val controller = controllerWithData(data)
      val result = controller.team("test-team").apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  "Retrieving a service" should {

    "return the internal source control name for an internal repo" in {
      val controller = controllerWithData(singleRepoResult(repoName = "r1", repoUrl = "ru", isInternal = false))
      val result = controller.repositoryDetails("r1").apply(FakeRequest())

      val githubLinks = (contentAsJson(result) \ "githubUrls").as[JsArray].value

      githubLinks.head.nameField mustBe "github-com"
      githubLinks.head.urlField mustBe "ru"
    }

    "Return a json representation of the service" in {
      val controller = controllerWithData(defaultData)
      val result = controller.repositoryDetails("repo-name").apply(FakeRequest())

      status(result) mustBe 200
      val json = contentAsJson(result)

      json.nameField mustBe "repo-name"
      json.teamNameSeq mustBe Seq("test-team")

      val environments = (json \ "environments").as[JsArray].value

      val env1Services = environments.find(_ \ "name" == JsString("env1")).value.as[JsObject] \ "services"
      val env1Links = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services = environments.find(_ \ "name" == JsString("env2")).value.as[JsObject] \ "services"
      val env2Links = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))
    }

    "Return a 404 when the serivce is not found" in {
      val controller = controllerWithData(defaultData)
      val result = controller.repositoryDetails("not-Found").apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  implicit class RichJsonValue(obj: JsValue) {
    def string(st: String): String = (obj \ st).as[String]

    def nameField = (obj \ "name").as[String]

    def urlField = (obj \ "url").as[String]

    def teamNameSeq = (obj \ "teamNames").as[Seq[String]]
  }

}
