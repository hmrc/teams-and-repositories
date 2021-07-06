/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.controller

import java.time.Instant

import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.libs.json._
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.teamsandrepositories.{GitRepository, RepoType}
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}
import uk.gov.hmrc.teamsandrepositories.controller.model.Repository
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.collection.immutable.ListMap
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class RepositoriesControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with Results
     with OptionValues
     with GuiceOneServerPerSuite
     with Eventually {

  private val now = Instant.now()

  private val createdDateForService1 = now.plusSeconds(1)
  private val createdDateForService2 = now.plusSeconds(2)
  private val createdDateForService3 = now.plusSeconds(3)
  private val createdDateForLib1     = now.plusSeconds(4)
  private val createdDateForLib2     = now.plusSeconds(5)

  private val lastActiveDateForService1 = now.plusSeconds(10)
  private val lastActiveDateForService2 = now.plusSeconds(20)
  private val lastActiveDateForService3 = now.plusSeconds(30)
  private val lastActiveDateForLib1     = now.plusSeconds(40)
  private val lastActiveDateForLib2     = now.plusSeconds(50)

  import play.api.inject.guice.GuiceApplicationBuilder

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "github.open.api.host" -> "http://bla.bla",
          "github.open.api.user" -> "",
          "github.open.api.key"  -> "",
          "metrics.jvm"          -> false
        )
      )
      .build

  val defaultData =
    Seq(
      new TeamRepositories(
        "test-team",
        List(
          GitRepository(
            "repo-name",
            "some description",
            "repo-url",
            createdDate        = createdDateForService1,
            lastActiveDate     = lastActiveDateForService1,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            archived           = false
          ),
          GitRepository(
            "library-repo",
            "some description",
            "library-url",
            createdDate        = createdDateForLib1,
            lastActiveDate     = lastActiveDateForLib1,
            repoType           = RepoType.Library,
            digitalServiceName = Some("digital-service-3"),
            language           = Some("Scala"),
            archived           = false
          )
        ),
        now
      ),
      new TeamRepositories(
        "another-team",
        List(
          GitRepository(
            "another-repo",
            "some description",
            "another-url",
            createdDate        = createdDateForService2,
            lastActiveDate     = lastActiveDateForService2,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            archived           = false
          ),
          GitRepository(
            "middle-repo",
            "some description",
            "middle-url",
            createdDate        = createdDateForService3,
            lastActiveDate     = lastActiveDateForService3,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            archived           = false
          ),
          GitRepository(
            "alibrary-repo",
            "some description",
            "library-url",
            createdDate        = createdDateForLib2,
            lastActiveDate     = lastActiveDateForLib2,
            repoType           = RepoType.Library,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            archived           = false
          ),
          GitRepository(
            "CATO-prototype",
            "some description",
            "prototype-url",
            createdDate        = createdDateForLib2,
            lastActiveDate     = lastActiveDateForLib2,
            repoType           = RepoType.Prototype,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            archived           = false
          ),
          GitRepository(
            "other-repo",
            "some description",
            "library-url",
            createdDate        = createdDateForLib2,
            lastActiveDate     = lastActiveDateForLib2,
            repoType           = RepoType.Other,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            archived           = false
          )
        ),
        now
      )
    )

  def singleRepoResult(
    teamName: String = "test-team",
    repoName: String = "repo-name",
    repoUrl: String = "repo-url"
  ): Seq[TeamRepositories] =
    Seq(
      TeamRepositories(
        "test-team",
        List(GitRepository(
          name           = repoName,
          description    = "some description",
          url            = repoUrl,
          createdDate    = now,
          lastActiveDate = now,
          repoType       = RepoType.Service,
          language       = Some("Scala"),
          archived       = false
        )),
        now
      )
    )

  "Teams controller" should {
    "have the correct url set up for the list of all services" in {
      uk.gov.hmrc.teamsandrepositories.controller.routes.RepositoriesController.services(details = false).url mustBe "/api/services"
    }
  }

  "Retrieving a list of digital services" should {
    "Return a json representation of the data" in new Setup {
      val result = controller.digitalServices.apply(FakeRequest())

      val digitalServices = contentAsJson(result).as[JsArray].value
      digitalServices.map(_.as[String]) mustBe Seq("digital-service-1", "digital-service-2", "digital-service-3")
    }
  }

  "Retrieving a list of all libraries" should {
    "return a name and dates list of all the libraries" in new Setup {
      val result       = controller.libraries(details = false)(FakeRequest())
      val resultJson   = contentAsJson(result)
      val libraryNames = resultJson.as[Seq[Repository]]
      libraryNames.map(_.name)          mustBe List("alibrary-repo", "library-repo")
      libraryNames.map(_.createdAt)     must contain theSameElementsAs List(createdDateForLib1, createdDateForLib2)
      libraryNames.map(_.lastUpdatedAt) must contain theSameElementsAs List(lastActiveDateForLib1, lastActiveDateForLib2 )
    }

    "Return a json representation of the data when request has a details query parameter" in new Setup {
      val result = controller.libraries(details = true).apply(FakeRequest())

      val resultJson = contentAsJson(result)

      val libraryNames = resultJson.as[Seq[JsObject]].map(_.value("name").as[String])
      libraryNames mustBe List("alibrary-repo", "library-repo")

      val last = resultJson.as[Seq[JsObject]].last

      (last \ "githubUrl").as[JsObject].as[Map[String, String]] mustBe Map(
        "name"        -> "github-com",
        "displayName" -> "GitHub.com",
        "url"         -> "library-url"
      )

      nameField(last) mustBe "library-repo"
      teamNamesField(last) mustBe Seq("test-team")

      val ciDetails: Seq[JsValue] = (last \ "ci").as[JsArray].value
      ciDetails.size mustBe 1

      ciDetails(0).as[JsObject].as[Map[String, String]] mustBe Map(
        "name"        -> "Build",
        "displayName" -> "Build",
        "url"         -> "https://build.tax.service.gov.uk/job/test-team/job/library-repo")
    }
  }

  "GET /api/repository_teams" should {
    "return service -> team mappings" in new Setup {
      val result = controller.repositoryTeams(FakeRequest())

      val data = contentAsJson(result).as[Map[String, Seq[String]]]

      data mustBe Map(
        "repo-name"      -> Seq("test-team"),
        "library-repo"   -> Seq("test-team"),
        "another-repo"   -> Seq("another-team"),
        "middle-repo"    -> Seq("another-team"),
        "alibrary-repo"  -> Seq("another-team"),
        "CATO-prototype" -> Seq("another-team"),
        "other-repo"     -> List("another-team")
      )
    }
  }

  "GET /api/services" should {
    "return a json representation of all the services sorted alphabetically when the request has a details query parameter" in new Setup {
      val result = controller.services(details = true)(FakeRequest())

      val resultJson = contentAsJson(result)

      resultJson.as[Seq[JsObject]].map(_.value("name").as[String]) mustBe List(
        "another-repo",
        "middle-repo",
        "repo-name"
      )

      val last = resultJson.as[Seq[JsObject]].last

      (last \ "githubUrl").as[JsObject].as[Map[String, String]] mustBe Map(
        "name"        -> "github-com",
        "displayName" -> "GitHub.com",
        "url"         -> "repo-url"
      )

      nameField(last) mustBe "repo-name"
      teamNamesField(last) mustBe Seq("test-team")

      val environments = (last \ "environments").as[JsArray].value

      val find: Option[JsValue] = environments.find(x => nameField(x) == "env1")
      val env1Services          = find.value.as[JsObject] \ "services"
      val env1Links             = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services = environments.find(x => nameField(x) == "env2").value.as[JsObject] \ "services"
      val env2Links    = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))
    }

    "return a json representation of the data sorted alphabetically when the request doesn't have a details query parameter" in new Setup {
      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(defaultData))

      val result = controller.services(details = false)(FakeRequest())

      val serviceList = contentAsJson(result).as[Seq[Repository]]
      serviceList.map(_.name) mustBe Seq("another-repo", "middle-repo", "repo-name")
      serviceList.map(_.createdAt) must contain theSameElementsAs List(
        createdDateForService1,
        createdDateForService2,
        createdDateForService3
      )
      serviceList.map(_.lastUpdatedAt) must contain theSameElementsAs List(
        lastActiveDateForService1,
        lastActiveDateForService2,
        lastActiveDateForService3
      )
    }

    "ignore case when sorting alphabetically" in new Setup {
      val sourceData =
        Seq(
          TeamRepositories(
            "test-team",
            List(
              GitRepository(
                "Another-repo",
                "some description",
                "Another-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false
              ),
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false
              ),
              GitRepository(
                "aadvark-repo",
                "some description",
                "aadvark-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false
              )
            ),
            now
          )
        )

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(sourceData))

      val result = controller.services(details = false)(FakeRequest())

      contentAsJson(result).as[List[Repository]].map(_.name) mustBe List("aadvark-repo", "Another-repo", "repo-name")
    }

    //TODO this should not be a controller test
    "flatten team info if a service belongs to multiple teams" in new Setup {
      val data =
        Seq(
          TeamRepositories(
            "test-team",
            List(
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false
              )
            ),
            now
          ),
          TeamRepositories(
            "another-team",
            List(
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false
              )
            ),
            now
          )
        )

      when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(data))

      val result = controller.services(details = false)(FakeRequest())

      contentAsJson(result).as[JsArray].value.size mustBe 1
    }
  }

  "Retrieving a service" should {
    "Return a json representation of the service" in new Setup {
      val result = controller.repositoryDetails("repo-name").apply(FakeRequest())

      status(result) mustBe 200
      val json = contentAsJson(result)

      nameField(json) mustBe "repo-name"
      teamNamesField(json) mustBe Seq("test-team")

      val environments = (json \ "environments").as[JsArray].value

      val env1Services = environments.find(x => (x \ "name").get == JsString("env1")).value.as[JsObject] \ "services"
      val env1Links    = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services = environments.find(x => (x \ "name").get == JsString("env2")).value.as[JsObject] \ "services"
      val env2Links    = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))
    }

    "Return a 404 when the serivce is not found" in new Setup {
      val result = controller.repositoryDetails("not-Found").apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  "Retrieving a list of all repositories" should {
    "return all the repositories" in new Setup {
      val result       = controller.repositories(None)(FakeRequest())
      val resultJson   = contentAsJson(result)
      val repositories = resultJson.as[Seq[Repository]]
      repositories.map(_.name) mustBe List(
        "alibrary-repo",
        "another-repo",
        "CATO-prototype",
        "library-repo",
        "middle-repo",
        "other-repo",
        "repo-name"
      )
    }
  }

  private def nameField(obj: JsValue): String =
    (obj \ "name").as[String]

  private def teamNamesField(obj: JsValue): Seq[String] =
    (obj \ "teamNames").as[Seq[String]]

  private trait Setup {
    val mockTeamsAndReposPersister = mock[TeamsAndReposPersister]
    val mockUrlTemplateProvider    = mock[UrlTemplatesProvider]

    when(mockTeamsAndReposPersister.getAllTeamsAndRepos(None))
      .thenReturn(Future.successful(defaultData))

    when(mockUrlTemplateProvider.ciUrlTemplates)
      .thenReturn(
        UrlTemplates(
          ListMap(
            "env1" -> Seq(UrlTemplate("log1", "log 1", "$name"), UrlTemplate("mon1", "mon 1", "$name")),
            "env2" -> Seq(UrlTemplate("log1", "log 1", "$name"))
          )
        )
      )

    val controller = new RepositoriesController(
      mockTeamsAndReposPersister,
      mockUrlTemplateProvider,
      stubControllerComponents()
    )
  }
}
