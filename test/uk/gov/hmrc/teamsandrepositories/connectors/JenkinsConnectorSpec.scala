/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.connectors

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.teamsandrepositories.models.BuildResult

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class JenkinsConnectorSpec extends AnyWordSpec with Matchers with ScalaFutures {
  import JenkinsApiReads._

  "JenkinsJob" should {
    "parse the root element" in {
      val json =
        """{
          "_class": "hudson.model.Hudson",
          "jobs":[
             {
                "_class": "com.cloudbees.hudson.plugins.folder.Folder",
                "name": "abc",
                "url": "https://jenkins/job/abc/"
             },
             {
                "_class": "com.cloudbees.hudson.plugins.folder.Folder",
                "name": "xyz",
                "url": "https://jenkins/job/xyz/"
             }
          ]
        }"""

      val x = Json
                .parse(json)
                .validate[JenkinsRoot]

      x shouldBe JsSuccess(
        JenkinsRoot(
          "hudson.model.Hudson",
          Seq(
            JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "abc", "https://jenkins/job/abc/", Seq()),
            JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/job/xyz/", Seq())
          )
        )
      )
    }

    "parse the build jobs" in {
      val json =
        """{
          |  "_class": "hudson.model.Hudson",
          |  "jobs": [
          |    {
          |      "_class": "hudson.model.FreeStyleProject",
          |      "name": "def",
          |      "url": "https://jenkins/job/abc/job/def/",
          |      "builds": [
          |        {
          |          "_class": "hudson.model.FreeStyleBuild",
          |          "number": 10933,
          |          "result": "SUCCESS",
          |          "timestamp": 1660042375930,
          |          "url": "https://jenkins/job/abc/job/def/10933/"
          |        }
          |      ]
          |    },
          |    {
          |      "_class": "hudson.model.FreeStyleProject",
          |      "name": "ghi",
          |      "url": "https://jenkins/job/abc/job/ghi/"
          |    }
          |  ]
          |}
          |""".stripMargin

      val x = Json.parse(json).validate[JenkinsRoot]
      x shouldBe JsSuccess(
        JenkinsRoot(
          "hudson.model.Hudson",
          Seq(
            JenkinsJob("hudson.model.FreeStyleProject", "def", "https://jenkins/job/abc/job/def/",
              Seq(JenkinsBuildData(10933, "https://jenkins/job/abc/job/def/10933/",
                Instant.ofEpochMilli(1660042375930L), Some(BuildResult.Success)))),
            JenkinsJob("hudson.model.FreeStyleProject", "ghi", "https://jenkins/job/abc/job/ghi/", Seq())
          )
        )
      )
    }

    "parse the folder element" in {
      val json =
        """{
          "_class": "com.cloudbees.hudson.plugins.folder.Folder",
          "jobs":[
             {
                "_class": "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                "name": "def",
                "url": "https://jenkins/job/abc/job/def/"
             },
             {
                "_class": "hudson.model.FreeStyleProject",
                "name": "ghi",
                "url": "https://jenkins/job/abc/job/ghi/"
             },
             {
                "_class": "com.cloudbees.hudson.plugins.folder.Folder",
                "name": "abc",
                "url": "https://jenkins/job/xyz/job/abc/"
             }
          ]
        }"""

      val x = Json
                .parse(json)
                .validate[JenkinsRoot]

      x shouldBe JsSuccess(
        JenkinsRoot(
          "com.cloudbees.hudson.plugins.folder.Folder",
          Seq(
            JenkinsJob("org.jenkinsci.plugins.workflow.job.WorkflowJob", "def", "https://jenkins/job/abc/job/def/", Seq()),
            JenkinsJob("hudson.model.FreeStyleProject"                 , "ghi", "https://jenkins/job/abc/job/ghi/", Seq()),
            JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder"    , "abc", "https://jenkins/job/xyz/job/abc/", Seq())
          )
        )
      )
    }
  }

  "JenkinsConnector.parse" should {
    "only returns FreeStyleProject" in {
      val mockLookup = (s: String) => Future.successful(Some(JenkinsRoot("hudson.model.Hudson", Seq.empty)))

      val mockRoot = Some(JenkinsRoot(
        "hudson.model.Hudson",
        Seq(
          JenkinsJob("hudson.model.FreeStyleProject"                 , "def", "https://jenkins/job/abc/job/def/", Seq()),
          JenkinsJob("org.jenkinsci.plugins.workflow.job.WorkflowJob", "xyz", "https://jenkins/job/abc/job/xyz/", Seq())
        )
      ))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head._class shouldBe "hudson.model.FreeStyleProject"
    }

    "get content from Folder" in {
      val mockLookup = (s: String) =>
        Future.successful(Some(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq(JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123", Seq())))))

      val mockRoot = Some(JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/456", Seq()))))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head shouldBe JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123", Seq())
    }

    "gets content from nested Folders" in {
      val mockLookup = (s: String) => s match {
        case "https://jenkins/456" => Future.successful(Some(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "hij", "https://jenkins/789", Seq())))))
        case "https://jenkins/789" => Future.successful(Some(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq(JenkinsJob("hudson.model.FreeStyleProject"             , "abc", "https://jenkins/123", Seq())))))
      }

      val mockRoot = Some(JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/456", Seq()))))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head shouldBe JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123", Seq())
    }

    "only returns FreeStyleProjects from Folders" in {
      val mockLookup = (s: String) => s match {
        case "https://jenkins/456" => Future.successful(Some(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "hij", "https://jenkins/789", Seq())))))
        case "https://jenkins/789" => Future.successful(Some(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq(
          JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123", Seq()),
          JenkinsJob("org.jenkinsci.plugins.workflow.job.WorkflowJob", "lmn", "https://jenkins/654", Seq())
        ))))
      }

      val mockRoot = Some(JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/456", Seq()))))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head shouldBe JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123", Seq())
    }
  }
}
