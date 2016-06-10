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

package uk.gov.hmrc.teamsandservices

import java.time.LocalDateTime

import org.mockito.Mockito._
import org.scalatest.OptionValues
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandservices.config.{UrlTemplate, UrlTemplates}

import scala.concurrent.Future

class TeamsServicesControllerSpec extends PlaySpec with MockitoSugar with Results with OptionValues{

  val timestamp = LocalDateTime.of(2016, 4, 5, 12, 57, 10)

  def controllerWithData(data: CachedResult[Seq[TeamRepositories]]) = {
    val fakeDataSource = mock[CachingRepositoryDataSource[Seq[TeamRepositories]]]
    when(fakeDataSource.getCachedTeamRepoMapping).thenReturn(Future.successful(data))

    new TeamsServicesController {
      override def dataSource = fakeDataSource
      override def ciUrlTemplates  = new UrlTemplates(
        Seq(new UrlTemplate("open","$name")),
        Seq(new UrlTemplate("closed","$name")),
        Map(
          "env1" -> Seq(
            new UrlTemplate("kibana","$name"),
            new UrlTemplate("grafana","$name")),
          "env2" -> Seq(
            new UrlTemplate("kibana","$name"))
        ))
    }
  }

  val defaultData = new CachedResult[Seq[TeamRepositories]](
    Seq(
      new TeamRepositories("test-team", List(
        Repository("repo-name", "repo-url", deployable = true))),
      new TeamRepositories("another-team", List(
        Repository("another-repo", "another-url", deployable = true),
        Repository("middle-repo", "middle-url", deployable = true)))
    ),
    timestamp)



  "Teams controller" should {

    "have the correct url set up for the teams list" in {
      uk.gov.hmrc.teamsandservices.routes.TeamsServicesController.teams().url mustBe "/api/teams"
    }

    "have the correct url set up for a team's services" in {
      uk.gov.hmrc.teamsandservices.routes.TeamsServicesController.teamServices("test-team").url mustBe "/api/teams/test-team/services"
    }

    "have the correct url set up for the list of all services" in {
      uk.gov.hmrc.teamsandservices.routes.TeamsServicesController.services().url mustBe "/api/services"
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

  "Retrieving a list of services for a team" should {

    "Return a json representation of the data, including the cache timestamp" in {
      val controller = controllerWithData(defaultData)
      val result = controller.teamServices("test-team").apply(FakeRequest())

      val timestampHeader = header("x-cache-timestamp", result)
      val data = contentAsJson(result).as[JsArray].value

      timestampHeader.value mustBe "Tue, 5 Apr 2016 12:57:10 GMT"
      data.length mustBe 1

      val service = data.head
      (service \ "name").as[String] mustBe "repo-name"

      val githubLinks = (service \ "githubUrls").as[JsArray].value
      (githubLinks(0) \ "name").as[String] mustBe "github-open"
      (githubLinks(0) \ "url").as[String] mustBe "repo-url"

    }

    "Return information about all the teams that have access to a repo" in {
      val sourceData = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(Repository("repo-name", "repo-url", deployable = true))),
          new TeamRepositories("another-team", List(Repository("repo-name", "repo-url", deployable = true)))
        ),
        timestamp)

      val controller = controllerWithData(sourceData)
      val result = controller.teamServices("another-team").apply(FakeRequest())

      val data = contentAsJson(result).as[JsArray].value

      val service = data.head
      (service \ "teamNames").as[Seq[String]] mustBe Seq("test-team", "another-team")
    }
  }

  "Retrieving a list of all services" should {

    "Return a json representation of the data sorted alphabetically, including the cache timestamp" in {
      val controller = controllerWithData(defaultData)
      val result = controller.services().apply(FakeRequest())

      val json = contentAsJson(result)
      header("x-cache-timesamp", result)

      val first = json.as[JsArray].value.head
      val firstGithubLinks = (first \ "githubUrls").as[JsArray].value
      (first \ "name").as[String] mustBe "another-repo"
      (first \ "teamNames").as[Seq[String]] mustBe Seq("another-team")
      (firstGithubLinks(0) \ "name").as[String] mustBe "github-open"
      (firstGithubLinks(0) \ "url").as[String] mustBe "another-url"

      val second = json.as[JsArray].value(1)
      val secondGithubLinks = (second \ "githubUrls").as[JsArray].value
      (second \ "name").as[String] mustBe "middle-repo"
      (second \ "teamNames").as[Seq[String]] mustBe Seq("another-team")
      (secondGithubLinks(0) \ "name").as[String] mustBe "github-open"
      (secondGithubLinks(0) \ "url").as[String] mustBe "middle-url"

      val third = json.as[JsArray].value(2)
      val thirdGithubLinks = (third \ "githubUrls").as[JsArray].value
      (third \ "name").as[String] mustBe "repo-name"
      (third \ "teamNames").as[Seq[String]] mustBe Seq("test-team")
      (thirdGithubLinks(0) \ "name").as[String] mustBe "github-open"
      (thirdGithubLinks(0) \ "url").as[String] mustBe "repo-url"
    }

    //TODO this should not be a controller test
    "Ignore case when sorting alphabetically" in {
      val sourceData = new CachedResult[Seq[TeamRepositories]](
        Seq(new TeamRepositories("test-team", List(
          Repository("Another-repo", "Another-url", deployable = true),
          Repository("repo-name", "repo-url", deployable = true),
          Repository("aadvark-repo", "aadvark-url", deployable = true)))),
        timestamp)

      val controller = controllerWithData(sourceData)
      val result = controller.services().apply(FakeRequest())

      val json = contentAsJson(result)
      val first = json.as[JsArray].value.head
      val second = json.as[JsArray].value(1)
      val third = json.as[JsArray].value(2)

      (first \ "name").as[String] mustBe "aadvark-repo"
      (second \ "name").as[String] mustBe "Another-repo"
      (third \ "name").as[String] mustBe "repo-name"
    }

    //TODO this should not be a controller test
    "Flatten team info if a service belongs to multiple teams" in {

      val data = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(Repository("repo-name", "repo-url", deployable = true))),
          new TeamRepositories("another-team", List(Repository("repo-name", "repo-url", deployable = true)))
        ),
        timestamp)

      val controller = controllerWithData(data)
      val result = controller.services().apply(FakeRequest())

      val json = contentAsJson(result)

      val first = json.as[JsArray].value.head
      (first \ "name").as[String] mustBe "repo-name"
      (first \ "teamNames").as[Seq[String]] mustBe Seq("test-team", "another-team")

      val githubLinks = (first \ "githubUrls").as[JsArray].value
      (githubLinks(0) \ "name").as[String] mustBe "github-open"
      (githubLinks(0) \ "url").as[String] mustBe "repo-url"
    }

    //TODO this should not be a controller test
    "Treat as one service if an internal and an open repo exist" in {

      val data = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(
            Repository("repo-name", "repo-url", deployable = true, isInternal = true),
            Repository("repo-name", "repo-open-url", deployable = true, isInternal = false)))),
        timestamp)

      val controller = controllerWithData(data)
      val result = controller.services().apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[JsArray].value
      jsonData.length mustBe 1

      val first = jsonData.head
      (first \ "name").as[String] mustBe "repo-name"
      (first \ "teamNames").as[Seq[String]] mustBe Seq("test-team")

      val githubLinks = (first \ "githubUrls").as[JsArray].value

      (githubLinks(0) \ "name").as[String] mustBe "github"
      (githubLinks(0) \ "url").as[String] mustBe "repo-url"

      (githubLinks(1) \ "name").as[String] mustBe "github-open"
      (githubLinks(1) \ "url").as[String] mustBe "repo-open-url"

    }

    "Return an empty list if a team has no services" in {

      val data = new CachedResult[Seq[TeamRepositories]](
        Seq(
          new TeamRepositories("test-team", List(
            Repository("repo-name", "repo-url", deployable = false, isInternal = true),
            Repository("repo-open-name", "repo-open-url", deployable = false, isInternal = false)))),
        timestamp)

      val controller = controllerWithData(data)
      val result = controller.teamServices("test-team").apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[JsArray].value
      jsonData.length mustBe 0

    }

    "Return an empty list if a team has no repositories" in {

      val data = new CachedResult[Seq[TeamRepositories]](Seq(new TeamRepositories("test-team", List())), timestamp)

      val controller = controllerWithData(data)
      val result = controller.teamServices("test-team").apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[JsArray].value
      jsonData.length mustBe 0

    }

    "Return a 404 if a team does not exist at all" in {

      val data = new CachedResult[Seq[TeamRepositories]](Seq(), timestamp)

      val controller = controllerWithData(data)
      val result = controller.teamServices("test-team").apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  "Retrieving a service" should {

    "Return a json representation of the service" in {
      val controller = controllerWithData(defaultData)
      val result = controller.service("repo-name").apply(FakeRequest())

      status(result) mustBe 200
      val json = contentAsJson(result)

      val githubLinks = (json \ "githubUrls").as[JsArray].value
      (json \ "name").as[String] mustBe "repo-name"
      (json \ "teamNames").as[Seq[String]] mustBe Seq("test-team")
      (githubLinks.head \ "name").as[String] mustBe "github-open"
      (githubLinks.head \ "url").as[String] mustBe "repo-url"

      val environments = (json \ "environments").as[JsArray].value
      
      val env1Services = environments.find(_ \ "name" == JsString("env1")).value.as[JsObject] \ "services"
      val env1Links = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "kibana", "url" -> "repo-name"),
        Map("name" -> "grafana", "url" -> "repo-name"))

      val env2Services = environments.find(_ \ "name" == JsString("env2")).value.as[JsObject] \ "services"
      val env2Links = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(
        Map("name" -> "kibana", "url" -> "repo-name"))
    }

    "Return a 404 when the serivce is not found" in {
      val controller = controllerWithData(defaultData)
      val result = controller.service("not-Found").apply(FakeRequest())

      status(result) mustBe 404
    }
  }
}
