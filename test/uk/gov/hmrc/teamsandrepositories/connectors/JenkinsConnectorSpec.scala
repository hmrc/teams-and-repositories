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
import play.api.libs.json.{JsResult, JsValue, Json}

class JenkinsConnectorSpec extends AnyWordSpec with Matchers with ScalaFutures {

  "JenkinsConnector.generateJobQuery" should {
    "get only top level of jobs when depth is 1" in {
      val res = JenkinsConnector.generateJobQuery(1, "")
      res shouldBe "jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result]]"
    }
    "get sub-jobs if depth = 2" in {
      val res = JenkinsConnector.generateJobQuery(2, "")
      res shouldBe "jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result]]]"
    }
    "get deep tree of jobs if depth = 10" in {
      val res = JenkinsConnector.generateJobQuery(10, "")
      res shouldBe "jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result],jobs[fullName,name,url,description,lastBuild[number,url,timestamp,result]]]]]]]]]]]"
    }
  }

  "JenkinsObject Json reads" should {
    "simple pipeline" in {
      val jsonString: JsValue = Json.parse(
        """{
          "_class": "org.jenkinsci.plugins.workflow.job.WorkflowJob",
          "description": "Some Pipeline",
          "fullName": "A Pipeline",
          "name": "Pipeline",
          "url": "https://...",
          "lastBuild": {
              "_class": "org.jenkinsci.plugins.workflow.job.WorkflowRun",
              "number": 42,
              "result": "SUCCESS",
              "timestamp": 1658927101317,
              "url": "https://..."
          }
    }"""
      )
      val residentFromJson: JsResult[JenkinsObject] =
        Json.fromJson[JenkinsObject](jsonString)
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsPipeline"
    }
    "simple job" in {
      val jsonString: JsValue = Json.parse(
        """{
            "_class": "hudson.model.FreeStyleProject",
            "description": "Job",
            "fullName": "Job",
            "name": "job",
            "url": "https://.../",
            "lastBuild": {
                "_class": "hudson.model.FreeStyleBuild",
                "number": 3,
                "result": "SUCCESS",
                "timestamp": 1658499777096,
                "url": "https://.../3/"
            }
      }"""
      )
      val residentFromJson: JsResult[JenkinsObject] =
        Json.fromJson[JenkinsObject](jsonString)
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsProject"
    }
    "simple folder" in {
      val jsonString: JsValue = Json.parse(
        """{
          "_class": "com.cloudbees.hudson.plugins.folder.Folder",
          "description": "This folder contains stuff",
          "fullName": "Folder",
          "name": "Folder",
          "url": "https://...",
         "jobs" : []
    }"""
      )
      val residentFromJson: JsResult[JenkinsObject] =
        Json.fromJson[JenkinsObject](jsonString)
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsFolder"
    }
    "Folder with a job" in {
      val jsonString: JsValue = Json.parse(
        """{
          "_class": "com.cloudbees.hudson.plugins.folder.Folder",
          "description": "This folder contains stuff",
          "fullName": "Folder",
          "name": "Folder",
          "url": "https://...",
         "jobs" : [{
                    "_class": "org.jenkinsci.plugins.workflow.job.WorkflowJob",
                    "description": "Some Job",
                    "fullName": "Job",
                    "name": "Job",
                    "url": "https://...",
                    "lastBuild": {
                        "_class": "org.jenkinsci.plugins.workflow.job.WorkflowRun",
                        "number": 42,
                        "result": "SUCCESS",
                        "timestamp": 1658927101317,
                        "url": "https://..."
                    }
              }]
    }"""
      )
      val residentFromJson: JsResult[JenkinsObject] =
        Json.fromJson[JenkinsObject](jsonString)
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsFolder"
    }
    "simple wrapper" in {
      val jsonString: JsValue = Json.parse(
        """{
        "_class": "hudson.model.Hudson",
       "jobs" : []
        }"""
      )
      val residentFromJson: JsResult[JenkinsBuildJobsWrapper] =
        Json.fromJson[JenkinsBuildJobsWrapper](jsonString)
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsBuildJobsWrapper"
    }
    "wrapper with folder" in {
      val jsonString: JsValue = Json.parse(
        """{
        "_class": "hudson.model.Hudson",
       "jobs" : [{
                  "_class": "com.cloudbees.hudson.plugins.folder.Folder",
                  "description": "This folder contains stuff",
                  "fullName": "Folder",
                  "name": "Folder",
                  "url": "https://...",
                 "jobs" : []
            }]
        }"""
      )
      val residentFromJson: JsResult[JenkinsBuildJobsWrapper] =
        Json.fromJson[JenkinsBuildJobsWrapper](jsonString)
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.connectors.JenkinsBuildJobsWrapper"
    }
  }
}
