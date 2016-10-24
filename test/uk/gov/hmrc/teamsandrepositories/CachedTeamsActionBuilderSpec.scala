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

import akka.stream.Materializer
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest, PlaySpec}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Result, Results}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class CachedTeamsActionBuilderSpec extends PlaySpec with WithFakeApplication{

  implicit lazy val materializer: Materializer = fakeApplication.materializer

  "CachedTeamsActionBuilder" should {
    "return the cache timestamp header and act on the cached result" in {
      val aResult: Future[CachedResult[Seq[TeamRepositories]]] =
        Future.successful(new CachedResult(
          Seq(TeamRepositories("teamName", List(Repository("repo1", "", true, RepoType.Deployable)))), LocalDateTime.of(2000, 1, 1, 1, 1, 1)))

      val result: Future[Result] = Helpers.call(CachedTeamsActionBuilder(() => aResult) { request =>
        Results.Ok(request.teams.size.toString)
      }, FakeRequest())

      header("X-Cache-Timestamp", result) mustBe  Some("Sat, 1 Jan 2000 01:01:01 GMT")

      contentAsString(result) mustBe "1"
    }



  }
}
