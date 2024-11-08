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

import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.teamsandrepositories.models.RepoType
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class JenkinsControllerSpec
  extends AnyWordSpec
    with Matchers
    with Results
    with MockitoSugar:

  "JenkinsController" should:
    "return a single match as Json" in new Setup:
      when(mockJenkinsJobsPersistence.findByJobName("job-foo"))
        .thenReturn(
          Future.successful(
            Some(
              JenkinsJobsPersistence.Job(
                repoName    = "repo-one",
                jobName     = "job-foo",
                jenkinsUrl  = "http://bar/job/api/",
                jobType     = JenkinsJobsPersistence.JobType.Job,
                repoType    = Some(RepoType.Service),
                testType    = None,
                latestBuild = None
              )
            )
          )
        )

      val result: Future[Result] =
        controller.lookup("job-foo").apply(FakeRequest())
        
      val bodyText: String =
        contentAsString(result)
        
      bodyText shouldBe """{"repoName":"repo-one","jobName":"job-foo","jenkinsURL":"http://bar/job/api/","jobType":"job","repoType":"Service"}"""

    "return a not found when no matches found" in new Setup:
      when(mockJenkinsJobsPersistence.findByJobName("bar"))
        .thenReturn(Future.successful(None))

      val result: Future[Result] =
        controller.lookup("bar").apply(FakeRequest())
      
      status(result) shouldBe 404

  trait Setup:
    val mockJenkinsJobsPersistence: JenkinsJobsPersistence = mock[JenkinsJobsPersistence]

    val controller: JenkinsController =
      JenkinsController(
        mockJenkinsJobsPersistence,
        stubControllerComponents()
      )
