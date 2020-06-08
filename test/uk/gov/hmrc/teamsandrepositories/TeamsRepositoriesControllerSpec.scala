/*
 * Copyright 2020 HM Revenue & Customs
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

import java.lang.System.currentTimeMillis
import java.time.LocalDateTime
import java.util.Date

import akka.actor.ActorSystem
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json._
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}
import uk.gov.hmrc.teamsandrepositories.controller.TeamsRepositoriesController
import uk.gov.hmrc.teamsandrepositories.controller.model.{Repository, Team}
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.collection.immutable.ListMap
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class TeamsRepositoriesControllerSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with Results
    with OptionValues
    with GuiceOneServerPerSuite
    with Eventually {

  private val now             = new Date().getTime
  private val updateTimestamp = LocalDateTime.of(2016, 4, 5, 12, 57, 10)

  private val createdDateForService1 = 1
  private val createdDateForService2 = 2
  private val createdDateForService3 = 3
  private val createdDateForLib1     = 4
  private val createdDateForLib2     = 5

  private val lastActiveDateForService1 = 10
  private val lastActiveDateForService2 = 20
  private val lastActiveDateForService3 = 30
  private val lastActiveDateForLib1     = 40
  private val lastActiveDateForLib2     = 50

  import play.api.inject.guice.GuiceApplicationBuilder

  val dataReloadScheduler        = mock[DataReloadScheduler]
  val mockTeamsAndReposPersister = mock[TeamsAndReposPersister]
  val mockUrlTemplateProvider    = mock[UrlTemplatesProvider]
  val mockConfiguration          = mock[Configuration]

  val mockTeamsAndRepositories = mock[TeamsAndReposPersister]

  import org.mockito.Mockito._

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "github.open.api.host" -> "http://bla.bla",
          "github.open.api.user" -> "",
          "github.open.api.key"  -> "",
          "metrics.jvm" -> false
        )
      )
      .build

  def controllerWithData(
    mockedReturnData: Seq[TeamRepositories],
    listOfReposToIgnore: List[String] = List.empty[String],
    actorSystem: Option[ActorSystem]  = None,
    updateTimestamp: LocalDateTime): TeamsRepositoriesController = {

    when(mockConfiguration.getOptional[Seq[String]]("shared.repositories")).thenReturn(Some(listOfReposToIgnore))
    when(mockTeamsAndRepositories.getAllTeamsAndRepos(None)).thenReturn(Future.successful(mockedReturnData))

    when(mockUrlTemplateProvider.ciUrlTemplates).thenReturn(
      UrlTemplates(
        ListMap(
          "env1" -> Seq(UrlTemplate("log1", "log 1", "$name"), UrlTemplate("mon1", "mon 1", "$name")),
          "env2" -> Seq(UrlTemplate("log1", "log 1", "$name"))
        )
      ))

    new TeamsRepositoriesController(
      dataReloadScheduler,
      mockTeamsAndReposPersister,
      mockUrlTemplateProvider,
      mockConfiguration,
      mockTeamsAndRepositories,
      stubControllerComponents()) {

      override lazy val repositoriesToIgnore = listOfReposToIgnore
    }
  }

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
        currentTimeMillis()
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
        currentTimeMillis()
      )
    )

  def singleRepoResult(teamName: String = "test-team", repoName: String = "repo-name", repoUrl: String = "repo-url") =
    Seq(
      new TeamRepositories(
        "test-team",
        List(
          GitRepository(
            name           = repoName,
            description    = "some description",
            url            = repoUrl,
            createdDate    = now,
            lastActiveDate = now,
            repoType       = RepoType.Service,
            language       = Some("Scala"),
            archived       = false
          )),
        currentTimeMillis()
      ))

  "Teams controller" should {

    "have the correct url set up for the teams list" in {
      uk.gov.hmrc.teamsandrepositories.controller.routes.TeamsRepositoriesController.teams.url mustBe "/api/teams"
    }

    "have the correct url set up for a team's services" in {
      uk.gov.hmrc.teamsandrepositories.controller.routes.TeamsRepositoriesController
        .repositoriesByTeam("test-team")
        .url mustBe "/api/teams/test-team"
    }

    "have the correct url set up for the list of all services" in {
      uk.gov.hmrc.teamsandrepositories.controller.routes.TeamsRepositoriesController.services.url mustBe "/api/services"
    }

  }

  "Retrieving a list of teams" should {

    "Return a json representation of the data" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result     = controller.teams.apply(FakeRequest())

      val team = contentAsJson(result).as[JsArray].value.head
      team.as[Team].name mustBe "test-team"
    }
  }

  "Retrieving a list of digital services" should {

    "Return a json representation of the data" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result     = controller.digitalServices.apply(FakeRequest())

      val digitalServices = contentAsJson(result).as[JsArray].value
      digitalServices.map(_.as[String]) mustBe Seq("digital-service-1", "digital-service-2", "digital-service-3")
    }
  }

  "Retrieving a list of repositories for a team" should {

    "Return all repo types belonging to a team" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)

      val result = controller.repositoriesByTeam("another-team").apply(FakeRequest())

      val data = contentAsJson(result).as[Map[String, List[String]]]
      data mustBe Map(
        "Service"   -> List("another-repo", "middle-repo"),
        "Library"   -> List("alibrary-repo"),
        "Prototype" -> List("CATO-prototype"),
        "Other"     -> List("other-repo")
      )
    }

    "Return information about all the teams that have access to a repo" in {
      val sourceData =
        Seq(
          new TeamRepositories(
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
                archived       = false)),
            currentTimeMillis()
          ),
          new TeamRepositories(
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
                archived       = false)),
            currentTimeMillis()
          )
        )

      val controller = controllerWithData(sourceData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoriesByTeam("another-team").apply(FakeRequest())

      contentAsJson(result)
        .as[Map[String, List[String]]] mustBe Map(
        "Service"   -> List("repo-name"),
        "Library"   -> List(),
        "Prototype" -> List(),
        "Other"     -> List())
    }
  }

  "Retrieving a list of repository details for a team" should {

    "Return all repo types belonging to a team" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoriesWithDetailsByTeam("another-team").apply(FakeRequest())

      val data = contentAsJson(result).as[Team]

      data.repos.value mustBe Map(
        RepoType.Service   -> List("another-repo", "middle-repo"),
        RepoType.Library   -> List("alibrary-repo"),
        RepoType.Prototype -> List("CATO-prototype"),
        RepoType.Other     -> List("other-repo")
      )
    }

    "Return the repository information for the specified team" in {
      val sourceData =
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
                archived       = false)),
            currentTimeMillis()
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
                archived       = false)),
            currentTimeMillis()
          )
        )

      val controller = controllerWithData(sourceData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoriesWithDetailsByTeam("another-team").apply(FakeRequest())

      contentAsJson(result)
        .as[Team]
        .repos
        .value mustBe Map(
        RepoType.Service   -> List("repo-name"),
        RepoType.Library   -> List(),
        RepoType.Prototype -> List(),
        RepoType.Other     -> List())
    }
  }

  "Retrieving a list of all libraries" should {

    "return a name and dates list of all the libraries" in {
      val controller   = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result       = controller.libraries(FakeRequest())
      val resultJson   = contentAsJson(result)
      val libraryNames = resultJson.as[Seq[Repository]]
      libraryNames.map(_.name) mustBe List("alibrary-repo", "library-repo")
      libraryNames.map(_.createdAt)     must contain theSameElementsAs List(createdDateForLib1, createdDateForLib2)
      libraryNames.map(_.lastUpdatedAt) must contain theSameElementsAs List(
        lastActiveDateForLib1,
        lastActiveDateForLib2)
    }

    "Return a json representation of the data when request has a details query parameter" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)

      val result = controller.libraries.apply(FakeRequest("GET", "/libraries?details=true"))

      val resultJson = contentAsJson(result)

      val libraryNames = resultJson.as[Seq[JsObject]].map(_.value("name").as[String])
      libraryNames mustBe List("alibrary-repo", "library-repo")

      val last = resultJson.as[Seq[JsObject]].last

      (last \ "githubUrl").as[JsObject].as[Map[String, String]] mustBe Map(
        "name"        -> "github-com",
        "displayName" -> "GitHub.com",
        "url"         -> "library-url"
      )

      last.nameField mustBe "library-repo"
      last.teamNameSeq mustBe Seq("test-team")

      val ciDetails: Seq[JsValue] = (last \ "ci").as[JsArray].value
      ciDetails.size mustBe 1

      ciDetails(0).as[JsObject].as[Map[String, String]] mustBe Map(
        "name"        -> "Build",
        "displayName" -> "Build",
        "url"         -> "https://build.tax.service.gov.uk/job/test-team/job/library-repo")
    }
  }

  "GET /api/services" should {

    "return a json representation of all the services sorted alphabetically when the request has a details query parameter" in new Setup {
      when(mockTeamsAndRepositories.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(defaultData))

      val result = controller.allServices(FakeRequest("GET", "/services?details=true"))

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

      last.nameField mustBe "repo-name"
      last.teamNameSeq mustBe Seq("test-team")

      val environments = (last \ "environments").as[JsArray].value

      val find: Option[JsValue] = environments.find(x => x.nameField == "env1")
      val env1Services          = find.value.as[JsObject] \ "services"
      val env1Links             = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services = environments.find(x => x.nameField == "env2").value.as[JsObject] \ "services"
      val env2Links    = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))
    }

    "return service -> team mappings for all services when the request has a teamDetails query parameter" in new Setup {
      when(mockTeamsAndRepositories.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(defaultData))

      val result = controller.allServices(FakeRequest("GET", "/services?teamDetails=true"))

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

    "return a json representation of the data sorted alphabetically when the request doesn't have a details query parameter" in new Setup {
      when(mockTeamsAndRepositories.getAllTeamsAndRepos(None))
        .thenReturn(Future.successful(defaultData))

      val result = controller.allServices(FakeRequest())

      val serviceList = contentAsJson(result).as[Seq[Repository]]
      serviceList.map(_.name) mustBe Seq("another-repo", "middle-repo", "repo-name")
      serviceList.map(_.createdAt) must contain theSameElementsAs List(
        createdDateForService1,
        createdDateForService2,
        createdDateForService3)
      serviceList.map(_.lastUpdatedAt) must contain theSameElementsAs List(
        lastActiveDateForService1,
        lastActiveDateForService2,
        lastActiveDateForService3)

    }

    "ignore case when sorting alphabetically" in {
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
                archived       = false),
              GitRepository(
                "repo-name",
                "some description",
                "repo-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false),
              GitRepository(
                "aadvark-repo",
                "some description",
                "aadvark-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Service,
                language       = Some("Scala"),
                archived       = false)
            ),
            currentTimeMillis()
          ))

      val controller = controllerWithData(sourceData, updateTimestamp = updateTimestamp)
      val result     = controller.allServices(FakeRequest())

      contentAsJson(result).as[List[Repository]].map(_.name) mustBe List("aadvark-repo", "Another-repo", "repo-name")
    }

    //TODO this should not be a controller test
    "flatten team info if a service belongs to multiple teams" in {
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
                archived       = false)),
            currentTimeMillis()
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
                archived       = false)),
            currentTimeMillis()
          )
        )

      val controller = controllerWithData(data, updateTimestamp = updateTimestamp)
      val result     = controller.allServices(FakeRequest())

      val json = contentAsJson(result)

      json.as[JsArray].value.size mustBe 1
    }

    "return the empty list for repository type if a team does not have it" in {
      val sourceData =
        Seq(
          new TeamRepositories(
            "test-team",
            List(
              GitRepository(
                name           = "repo-open-name",
                description    = "some description",
                url            = "repo-open-url",
                createdDate    = now,
                lastActiveDate = now,
                repoType       = RepoType.Library,
                language       = Some("Scala"),
                archived       = false
              )
            ),
            currentTimeMillis()
          ))

      val controller = controllerWithData(sourceData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoriesByTeam("test-team").apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[Map[String, List[String]]]
      jsonData.get("Service") mustBe Some(List())

    }

    "return an empty list if a team has no repositories" in {
      val sourceData = Seq(new TeamRepositories("test-team", List(), currentTimeMillis()))

      val controller = controllerWithData(sourceData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoriesByTeam("test-team").apply(FakeRequest())

      val json = contentAsJson(result)

      val jsonData = json.as[Map[String, List[String]]]
      jsonData mustBe Map(
        "Service"   -> List(),
        "Library"   -> List(),
        "Prototype" -> List(),
        "Other"     -> List()
      )

    }

    "return a 404 if a team does not exist at all" in {
      val sourceData = Seq.empty[TeamRepositories]

      val controller = controllerWithData(sourceData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoriesByTeam("test-team").apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  "POST /api/services" should {

    "return a json representation of services with the given names sorted alphabetically when the request has a details query parameter" in new Setup {
      when(mockTeamsAndRepositories.getTeamsAndRepos(List("repo1", "repo2")))
        .thenReturn(Future.successful(defaultData))

      val result =
        controller.services(FakeRequest("GET", "/services?details=true").withBody(Json.arr("repo1", "repo2")))

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

      last.nameField mustBe "repo-name"
      last.teamNameSeq mustBe Seq("test-team")

      val environments = (last \ "environments").as[JsArray].value

      val find: Option[JsValue] = environments.find(x => x.nameField == "env1")
      val env1Services          = find.value.as[JsObject] \ "services"
      val env1Links             = env1Services.as[List[Map[String, String]]].toSet
      env1Links mustBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services = environments.find(x => x.nameField == "env2").value.as[JsObject] \ "services"
      val env2Links    = env2Services.as[List[Map[String, String]]].toSet
      env2Links mustBe Set(Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))
    }

    "return service -> team mappings for selected services when the request has a teamDetails query parameter" in new Setup {
      when(mockTeamsAndRepositories.getTeamsAndRepos(List("service1")))
        .thenReturn(Future.successful(defaultData))

      val result = controller.services(FakeRequest("GET", "/services?teamDetails=true").withBody(Json.arr("service1")))

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

    "return an empty map if no service names given" in new Setup {

      val result = controller.services(FakeRequest("GET", "/services?teamDetails=true").withBody(Json.arr()))

      val data = contentAsJson(result).as[Map[String, Seq[String]]]

      data mustBe Map.empty
    }

    "return a json representation of the data sorted alphabetically when the request doesn't have a details query parameter" in new Setup {
      when(mockTeamsAndRepositories.getTeamsAndRepos(List("repo1", "repo2")))
        .thenReturn(Future.successful(defaultData))

      val result = controller.services(FakeRequest().withBody(Json.arr("repo1", "repo2")))

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
  }

  "Retrieving a service" should {
    "Return a json representation of the service" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoryDetails("repo-name").apply(FakeRequest())

      status(result) mustBe 200
      val json = contentAsJson(result)

      json.nameField mustBe "repo-name"
      json.teamNameSeq mustBe Seq("test-team")

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

    "Return a 404 when the serivce is not found" in {
      val controller = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result     = controller.repositoryDetails("not-Found").apply(FakeRequest())

      status(result) mustBe 404
    }
  }

  "Retrieving a list of all repositories" should {

    "return all the repositories" in {

      val controller   = controllerWithData(defaultData, updateTimestamp = updateTimestamp)
      val result       = controller.allRepositories(None)(FakeRequest())
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

  "resetLastActiveDate" should {

    "return OK if the persister return Some" in new Setup {

      val repoName = "repo-name"

      when(mockTeamsAndReposPersister.resetLastActiveDate(repoName)).thenReturn(Future.successful(Some(1)))

      val result = controller.resetLastActiveDate(repoName)(FakeRequest())
      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("message" -> s"'$repoName' last active date reset for 1 team(s)")
    }

    "return NOT_FOUND if the persister return None" in new Setup {

      val repoName = "repo-name"

      when(mockTeamsAndReposPersister.resetLastActiveDate(repoName)).thenReturn(Future.successful(None))

      val result = controller.resetLastActiveDate(repoName)(FakeRequest())
      status(result) mustBe NOT_FOUND
    }
  }

  implicit class RichJsonValue(obj: JsValue) {
    def string(st: String) = (obj \ st).as[String]
    def nameField          = (obj \ "name").as[String]
    def urlField           = (obj \ "url").as[String]
    def teamNameSeq        = (obj \ "teamNames").as[Seq[String]]
  }

  private trait Setup {
    when(mockUrlTemplateProvider.ciUrlTemplates).thenReturn(
      UrlTemplates(
        ListMap(
          "env1" -> Seq(UrlTemplate("log1", "log 1", "$name"), UrlTemplate("mon1", "mon 1", "$name")),
          "env2" -> Seq(UrlTemplate("log1", "log 1", "$name"))
        ))
    )

    val controller = new TeamsRepositoriesController(
      dataReloadScheduler,
      mockTeamsAndReposPersister,
      mockUrlTemplateProvider,
      mockConfiguration,
      mockTeamsAndRepositories,
      stubControllerComponents()
    )
  }
}
