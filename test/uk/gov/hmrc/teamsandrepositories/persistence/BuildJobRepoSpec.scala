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

package uk.gov.hmrc.teamsandrepositories.persistence

import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.teamsandrepositories.models.{BuildData, BuildResult, JenkinsObject}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global

class JenkinsLinksPersistenceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[JenkinsObject.StandardJob] {

  override protected val repository = new JenkinsLinksPersistence(mongoComponent)

  "BuildJobRepository" should {
    "putAll correctly" in {
      val job1 = mkBuildJob("service1Job", "service1")
      val job2 = mkBuildJob("service2Job", "service2")
      repository.putAll(Seq(job1, job2)).futureValue
      repository.findByJobName("service1Job").futureValue shouldBe Some(job1)
      repository.findByJobName("service2Job").futureValue shouldBe Some(job2)

      val job3 = mkBuildJob("service3Job", "service3")
      repository.putAll(Seq(job1, job3)).futureValue
      repository.findByJobName("service1Job").futureValue shouldBe Some(job1)
      repository.findByJobName("service2Job").futureValue shouldBe None
      repository.findByJobName("service3Job").futureValue shouldBe Some(job3)
    }

    "find all by repository name" in {
      val job1 = mkBuildJob("service1Job1", "service1")
      val job2 = mkBuildJob("service1Job2", "service1")
      val job3 = mkBuildJob("service2Job" , "service2")
      repository.putAll(Seq(job1, job2, job3)).futureValue
      repository.findAllByRepo("service1").futureValue shouldBe Seq(job1, job2)
      repository.findAllByRepo("service2").futureValue shouldBe Seq(job3)
    }
  }

  def mkBuildJob(jobName: String, repositoryName: String): JenkinsObject.StandardJob = {
    val jenkinsUrl  = s"https://build.tax.service.gov.uk/job/teamName/job/$jobName/"
    val buildNumber = 1
    JenkinsObject.StandardJob(
      name        = jobName,
      jobType     = None,
      jenkinsUrl  = s"https://build.tax.service.gov.uk/job/teamName/job/$repositoryName-job/",
      latestBuild = Some(BuildData(
                      number      = buildNumber,
                      url         = s"$jenkinsUrl$buildNumber",
                      timestamp   = Instant.now().truncatedTo(ChronoUnit.MILLIS),
                      result      = Some(BuildResult.Success),
                      description = Some(s"$repositoryName 1.0.0")
                    )),
      gitHubUrl   = Some(s"https://github.com/hmrc/$repositoryName.git")
    )
  }
}
