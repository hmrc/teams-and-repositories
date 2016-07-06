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

import org.scalatest.Matchers
import play.api.mvc.{Result, Results}
import play.api.test.{FakeRequest, PlaySpecification}

import scala.concurrent.Future

class CachedTeamsActionBuilderSpec extends PlaySpecification {

  "CachedTeamsActionBuilder" should {
    "return the cache timestamp header and act on the cached result" in {
      val aResult: Future[CachedResult[Seq[TeamRepositories]]] =
        Future.successful(new CachedResult(
          Seq(TeamRepositories("teamName", List(Repository("repo1", "", true, true)))), LocalDateTime.of(2000, 1, 1, 1, 1, 1)))

      val result: Future[Result] = call(CachedTeamsActionBuilder(() => aResult) { request =>
        Results.Ok(request.teams.size.toString)
      }, FakeRequest())

      header("X-Cache-Timestamp", result) shouldEqual Some("Sat, 1 Jan 2000 01:01:01 GMT")

      contentAsString(result) mustEqual "1"
    }

    "include repository with isDeployable=false as services if one of the repository with same name isDeployable" in {

      var result = Map.empty[String, Seq[Repository]]

      val aResult: Future[CachedResult[Seq[TeamRepositories]]] =
        Future.successful(new CachedResult(
          Seq(
            TeamRepositories("teamName", List(
              Repository("repo1", "", isInternal = false, isDeployable = true),
              Repository("repo2", "", isInternal = true, isDeployable = true),
              Repository("repo1", "", isInternal = true, isDeployable = false),
              Repository("repo3", "", isInternal = true, isDeployable = false)
            )
            ),
            TeamRepositories("teamNameOther", List(Repository("repo3", "", isInternal = true, isDeployable = false)))
          )
          , LocalDateTime.of(2000, 1, 1, 1, 1, 1))
        )

      val resultF: Future[Result] = call(CachedTeamsActionBuilder(() => aResult) { request =>

        result += ("repos" -> request.teams.flatMap(_.repositories))


        Results.Ok("Success")

      }, FakeRequest())


      contentAsString(resultF) must contain("Success")
      result("repos").toSet.size mustEqual 3
      result("repos").toSet mustEqual Seq(
        Repository("repo1", "", isInternal = false, isDeployable = true),
        Repository("repo2", "", isInternal = true, isDeployable = true),
        Repository("repo1", "", isInternal = true, isDeployable = false)
      ).toSet

    }
  }
}
