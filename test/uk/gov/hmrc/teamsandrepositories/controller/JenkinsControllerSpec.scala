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
import uk.gov.hmrc.teamsandrepositories.model.{GitRepository, RepoType}
import uk.gov.hmrc.teamsandrepositories.persistence.{JenkinsJobsPersistence, RepositoriesPersistence}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class JenkinsControllerSpec
  extends AnyWordSpec
     with Matchers
     with Results
     with MockitoSugar:

  "JenkinsController.lookup" should:
    "return a single match as Json" in new Setup:
      when(mockJenkinsJobsPersistence.findByJobName("job-foo"))
        .thenReturn:
          Future.successful(Some(job))

      val result: Future[Result] =
        controller.lookup("job-foo").apply(FakeRequest())

      status(result) shouldBe 200

      contentAsString(result) shouldBe
         """{"repoName":"repo-one","jobName":"job-foo","jenkinsURL":"http://bar/job/api/","jobType":"job","repoType":"Service"}"""

    "return a not found when no matches found" in new Setup:
      when(mockJenkinsJobsPersistence.findByJobName("bar"))
        .thenReturn(Future.successful(None))

      val result: Future[Result] =
        controller.lookup("bar").apply(FakeRequest())

      status(result) shouldBe 404

  "JenkinsController.findTestJobs" should:
    "return test jobs for repos matching team and digitalService" in new Setup:
      val team           = Some("team")
      val digitalService = Some("digitalService")
      val repos          = Seq.empty[GitRepository]
      val jobs           = Seq(job)

      when(mockRepositoriesPersistence.find(
        name               = None,
        team               = None,
        owningTeam         = team,
        digitalServiceName = digitalService,
        isArchived         = None,
        repoType           = None,
        serviceType        = None,
        tags               = None
      ))
        .thenReturn(Future.successful(repos))

      when(mockJenkinsJobsPersistence.findTests(Some(repos.map(_.name))))
        .thenReturn(Future.successful(jobs))

      val result: Future[Result] =
        controller.findTestJobs(Some("team"), Some("digitalService")).apply(FakeRequest())

      status(result) shouldBe 200

      contentAsString(result) shouldBe
         """[{"repoName":"repo-one","jobName":"job-foo","jenkinsURL":"http://bar/job/api/","jobType":"job","repoType":"Service"}]"""

    "return all test jobs" in new Setup:
      val jobs = Seq(job)

      when(mockJenkinsJobsPersistence.findTests(None))
        .thenReturn(Future.successful(jobs))

      val result: Future[Result] =
        controller.findTestJobs(None, None).apply(FakeRequest())

      status(result) shouldBe 200

      contentAsString(result) shouldBe
         """[{"repoName":"repo-one","jobName":"job-foo","jenkinsURL":"http://bar/job/api/","jobType":"job","repoType":"Service"}]"""

  val job =
    JenkinsJobsPersistence.Job(
      repoName    = "repo-one",
      jobName     = "job-foo",
      jenkinsUrl  = "http://bar/job/api/",
      jobType     = JenkinsJobsPersistence.JobType.Job,
      repoType    = Some(RepoType.Service),
      testType    = None,
      latestBuild = None
    )

  trait Setup:
    val mockJenkinsJobsPersistence: JenkinsJobsPersistence   = mock[JenkinsJobsPersistence]
    val mockRepositoriesPersistence: RepositoriesPersistence = mock[RepositoriesPersistence]

    val controller: JenkinsController =
      JenkinsController(
        mockJenkinsJobsPersistence,
        mockRepositoriesPersistence,
        stubControllerComponents()
      )
