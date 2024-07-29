/*
 * Copyright 2023 HM Revenue & Customs
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
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.libs.json.*
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.teamsandrepositories.models.*
import uk.gov.hmrc.teamsandrepositories.config.{UrlTemplate, UrlTemplates, UrlTemplatesProvider}
import uk.gov.hmrc.teamsandrepositories.controller.model.Repository
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class LegacyRepositoriesControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with Results
     with OptionValues
     with GuiceOneServerPerSuite
     with Eventually:

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
    GuiceApplicationBuilder()
      .configure(
        Map(
          "github.open.api.host" -> "http://bla.bla",
          "github.open.api.key"  -> "",
          "jenkins.username"     -> "",
          "jenkins.token"        -> "",
          "jenkins.url"          -> ""
        )
      )
      .build()

  val defaultData: Seq[TeamRepositories] =
    Seq(
      TeamRepositories(
        teamName     = "test-team",
        repositories = List(
          GitRepository(
            "repo-name",
            "some description",
            "repo-url",
            createdDate        = createdDateForService1,
            lastActiveDate     = lastActiveDateForService1,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-2"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
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
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate = Some(now),
        updateDate  = now
      ),
      TeamRepositories(
        teamName     = "another-team",
        repositories = List(
          GitRepository(
            "another-repo",
            "some description",
            "another-url",
            createdDate        = createdDateForService2,
            lastActiveDate     = lastActiveDateForService2,
            repoType           = RepoType.Service,
            digitalServiceName = Some("digital-service-1"),
            language           = Some("Scala"),
            isArchived         = false,
            defaultBranch      = "main"
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
            isArchived         = false,
            defaultBranch      = "main"
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
            isArchived         = false,
            defaultBranch      = "main"
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
            isArchived         = false,
            defaultBranch      = "main"
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
            isArchived         = false,
            defaultBranch      = "main"
          )
        ),
        createdDate = Some(now),
        updateDate  = now
      )
    )

  def singleRepoResult(
    teamName: String = "test-team",
    repoName: String = "repo-name",
    repoUrl: String = "repo-url"
  ): Seq[TeamRepositories] =
    Seq(
      TeamRepositories(
        teamName     = teamName,
        repositories = List(GitRepository(
          name           = repoName,
          description    = "some description",
          url            = repoUrl,
          createdDate    = now,
          lastActiveDate = now,
          repoType       = RepoType.Service,
          language       = Some("Scala"),
          isArchived     = false,
          defaultBranch  = "main"
        )),
        createdDate = Some(now),
        updateDate  = now
      )
    )

  "Retrieving a service" should:
    "Return a json representation of the service" in new Setup:
      val result: Future[Result] = controller.repositoryDetails("repo-name").apply(FakeRequest())

      status(result) shouldBe 200
      val json: JsValue = contentAsJson(result)

      nameField(json) shouldBe "repo-name"
      teamNamesField(json) shouldBe Seq("test-team")

      val environments: collection.IndexedSeq[JsValue] = (json \ "environments").as[JsArray].value

      val env1Services: JsLookupResult = environments.find(x => (x \ "name").get == JsString("env1")).value.as[JsObject] \ "services"
      val env1Links: Set[Map[String, String]] = env1Services.as[List[Map[String, String]]].toSet
      env1Links shouldBe Set(
        Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"),
        Map("name" -> "mon1", "displayName" -> "mon 1", "url" -> "repo-name"))

      val env2Services: JsLookupResult = environments.find(x => (x \ "name").get == JsString("env2")).value.as[JsObject] \ "services"
      val env2Links: Set[Map[String, String]] = env2Services.as[List[Map[String, String]]].toSet
      env2Links shouldBe Set(Map("name" -> "log1", "displayName" -> "log 1", "url" -> "repo-name"))

    "Return a 404 when the serivce is not found" in new Setup:
      val result: Future[Result] = controller.repositoryDetails("not-Found").apply(FakeRequest())

      status(result) shouldBe 404

  "Retrieving a list of all repositories" should:
    "return all the repositories" in new Setup:
      val result: Future[Result] = controller.repositories(None)(FakeRequest())
      val resultJson: JsValue = contentAsJson(result)
      val repositories: Seq[Repository] = resultJson.as[Seq[Repository]]
      repositories.map(_.name) shouldBe List(
        "alibrary-repo",
        "another-repo",
        "CATO-prototype",
        "library-repo",
        "middle-repo",
        "other-repo",
        "repo-name"
      )

    "return all the default branches of repositories" in new Setup:
      val result: Future[Result] = controller.repositories(None)(FakeRequest())
      val resultJson: JsValue = contentAsJson(result)
      val repositories: Seq[Repository] = resultJson.as[Seq[Repository]]
      repositories.map(_.defaultBranch).distinct shouldBe List("main")

  "return all the default branches of repositories" in new Setup:
    val result: Future[Result] = controller.repositories(None)(FakeRequest())
    val resultJson: JsValue = contentAsJson(result)
    val repositories: Seq[Repository] = resultJson.as[Seq[Repository]]
    repositories.map(_.defaultBranch).distinct shouldBe List("main")

  private def nameField(obj: JsValue): String =
    (obj \ "name").as[String]

  private def teamNamesField(obj: JsValue): Seq[String] =
    (obj \ "teamNames").as[Seq[String]]

  private trait Setup:
    val mockTeamsAndReposPersister: RepositoriesPersistence = mock[RepositoriesPersistence]
    val mockUrlTemplateProvider: UrlTemplatesProvider = mock[UrlTemplatesProvider]

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

    val controller: LegacyRepositoriesController =
      LegacyRepositoriesController(
        mockTeamsAndReposPersister,
        mockUrlTemplateProvider,
        stubControllerComponents()
      )
