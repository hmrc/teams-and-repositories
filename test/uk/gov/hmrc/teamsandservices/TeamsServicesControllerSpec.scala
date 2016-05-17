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

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsArray
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandservices.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}

import scala.concurrent.Future

class TeamsServicesControllerSpec extends PlaySpec with MockitoSugar with Results {

  val timestamp = new DateTime(2016, 4, 5, 12, 57)
  val data = new CachedResult[Seq[TeamRepositories]](
    Seq(
      new TeamRepositories("test-team", List(Repository("repo-name", "repo-url", deployable = true))),
      new TeamRepositories("another-team", List(
        Repository("another-repo", "another-url", deployable = true),
        Repository("middle-repo", "middle-url", deployable = true)))
    ),
    timestamp)

  val fakeDataSource = mock[CachingRepositoryDataSource]
  when(fakeDataSource.getCachedTeamRepoMapping).thenReturn(Future.successful(data))

  val controller = new TeamsServicesController {
    override def dataSource: CachingRepositoryDataSource = fakeDataSource
    override def ciUrlTemplates  = new UrlTemplates(
      Seq(new UrlTemplate("open","$name")),
      Seq(new UrlTemplate("closed","$name")))
  }

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

      val result = controller.teams().apply(FakeRequest())

      val json = contentAsJson(result)
      val team = (json \ "data").as[JsArray].value.head

      (json \ "cacheTimestamp").as[DateTime] mustBe timestamp
      team.as[String] mustBe "test-team"
    }
  }

  "Retrieving a list of services for a team" should {

    "Return a json representation of the data, including the cache timestamp" in {

      val result = controller.teamServices("test-team").apply(FakeRequest())

      val json = contentAsJson(result)
      val data = (json \ "data").as[JsArray].value

      data.length mustBe 1

      val service = data.head
      (json \ "cacheTimestamp").as[DateTime] mustBe timestamp

      (service \ "name").as[String] mustBe "repo-name"
      (service \ "githubUrl" \ "name").as[String] mustBe "github"
      (service \ "githubUrl" \ "url").as[String] mustBe "repo-url"

    }
  }

  "Retrieving a list of all services" should {

    "Return a json representation of the data sorted alphabetically, including the cache timestamp" in {

      val result = controller.services().apply(FakeRequest())

      val json = contentAsJson(result)
      (json \ "cacheTimestamp").as[DateTime] mustBe timestamp

      val first = (json \ "data").as[JsArray].value.head
      (first \ "name").as[String] mustBe "another-repo"
      (first \ "teamName").as[String] mustBe "another-team"
      (first \ "githubUrl" \ "name").as[String] mustBe "github"
      (first \ "githubUrl" \ "url").as[String] mustBe "another-url"

      val second = (json \ "data").as[JsArray].value(1)
      (second \ "name").as[String] mustBe "middle-repo"
      (second \ "teamName").as[String] mustBe "another-team"
      (second \ "githubUrl" \ "name").as[String] mustBe "github"
      (second \ "githubUrl" \ "url").as[String] mustBe "middle-url"

      val third = (json \ "data").as[JsArray].value(2)
      (third \ "name").as[String] mustBe "repo-name"
      (third \ "teamName").as[String] mustBe "test-team"
      (third \ "githubUrl" \ "name").as[String] mustBe "github"
      (third \ "githubUrl" \ "url").as[String] mustBe "repo-url"

    }
  }
}
