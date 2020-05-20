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

package uk.gov.hmrc.teamsandrepositories.connectors

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class JenkinsConnectorSpec extends AnyWordSpec with Matchers with ScalaFutures {
  import JenkinsApiReads._

  "JenkinsJob" should {
    "be parsable from Json" in {
      val json = "{}"
      val x = Json
                .parse(json)
                .validate[JenkinsRoot]

      x.isError shouldBe true
    }

    "parse the root element" in {
      val json =
        """
          |{
          |   "_class":"hudson.model.Hudson",
          |   "jobs":[
          |      {
          |         "_class":"com.cloudbees.hudson.plugins.folder.Folder",
          |         "name":"abc",
          |         "url":"https://jenkins/job/abc/"
          |      },
          |      {
          |         "_class":"com.cloudbees.hudson.plugins.folder.Folder",
          |         "name":"xyz",
          |         "url":"https://jenkins/job/xyz/"
          |      }
          |   ]
          |}
          |""".stripMargin

      val x = Json
                .parse(json)
                .validate[JenkinsRoot]

      x shouldBe JsSuccess(JenkinsRoot("hudson.model.Hudson",
        Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "abc", "https://jenkins/job/abc/"),
            JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/job/xyz/"))))
    }

    "parse the folder element" in {
      val json =
        """
          |{
          |   "_class":"com.cloudbees.hudson.plugins.folder.Folder",
          |   "jobs":[
          |      {
          |         "_class":"org.jenkinsci.plugins.workflow.job.WorkflowJob",
          |         "name":"def",
          |         "url":"https://jenkins/job/abc/job/def/"
          |      },
          |      {
          |         "_class":"hudson.model.FreeStyleProject",
          |         "name":"ghi",
          |         "url":"https://jenkins/job/abc/job/ghi/"
          |      },
          |      {
          |         "_class":"com.cloudbees.hudson.plugins.folder.Folder",
          |         "name":"abc",
          |         "url":"https://jenkins/job/xyz/job/abc/"
          |      }
          |   ]
          |}
          |""".stripMargin

      val x = Json
                .parse(json)
                .validate[JenkinsRoot]

      x shouldBe JsSuccess(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder",
        Seq(JenkinsJob("org.jenkinsci.plugins.workflow.job.WorkflowJob", "def", "https://jenkins/job/abc/job/def/"),
          JenkinsJob("hudson.model.FreeStyleProject", "ghi", "https://jenkins/job/abc/job/ghi/"),
          JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "abc", "https://jenkins/job/xyz/job/abc/"))))
    }
  }

  "JenkinsConnector.parse" should {
    "only returns FreeStyleProject" in {
      val mockLookup = (s: String) => Future.successful(JenkinsRoot("hudson.model.Hudson", Seq.empty[JenkinsJob]))

      val mockRoot = JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("hudson.model.FreeStyleProject", "def", "https://jenkins/job/abc/job/def/"),
        JenkinsJob("org.jenkinsci.plugins.workflow.job.WorkflowJob", "xyz", "https://jenkins/job/abc/job/xyz/")))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head._class shouldBe "hudson.model.FreeStyleProject"
    }

    "get content from Folder" in {
      val mockLookup = (s: String) => Future.successful(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq[JenkinsJob](JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123"))))

      val mockRoot = JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/456")))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head shouldBe JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123")
    }

    "gets content from nested Folders" in {
      val mockLookup = (s: String) => s match {
        case "https://jenkins/456" => Future.successful(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq[JenkinsJob](JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "hij", "https://jenkins/789"))))
        case "https://jenkins/789" => Future.successful(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq[JenkinsJob](JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123"))))
      }

      val mockRoot = JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/456")))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head shouldBe JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123")
    }

    "only returns FreeStyleProjects from Folders" in {
      val mockLookup = (s: String) => s match {
        case "https://jenkins/456" => Future.successful(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq[JenkinsJob](JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "hij", "https://jenkins/789"))))
        case "https://jenkins/789" => Future.successful(JenkinsRoot("com.cloudbees.hudson.plugins.folder.Folder", Seq[JenkinsJob](JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123"), JenkinsJob("org.jenkinsci.plugins.workflow.job.WorkflowJob", "lmn", "https://jenkins/654"))))
      }

      val mockRoot = JenkinsRoot("hudson.model.Hudson", Seq(JenkinsJob("com.cloudbees.hudson.plugins.folder.Folder", "xyz", "https://jenkins/456")))

      val res = JenkinsConnector.parse(mockRoot, mockLookup).futureValue

      res.length shouldBe 1
      res.head shouldBe JenkinsJob("hudson.model.FreeStyleProject", "abc", "https://jenkins/123")
    }
  }
}
