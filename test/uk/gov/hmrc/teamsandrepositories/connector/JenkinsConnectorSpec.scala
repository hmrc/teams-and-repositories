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

package uk.gov.hmrc.teamsandrepositories.connector

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, JsValue, Json}
import java.time.Instant


class JenkinsConnectorSpec extends AnyWordSpec
  with Matchers:

  "JenkinsConnector.generateJobQuery" should:
    "get only top level of jobs when depth is 1" in:
      val res = JenkinsConnector.generateJobQuery(1)
      res shouldBe "jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]]]"

    "get sub-jobs if depth = 2" in:
      val res = JenkinsConnector.generateJobQuery(2)
      res shouldBe "jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]]]]"

    "get deep tree of jobs if depth = 10" in:
      val res = JenkinsConnector.generateJobQuery(10)
      res shouldBe "jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]],jobs[fullName,name,url,lastBuild[number,url,timestamp,result,description],scm[userRemoteConfigs[url]]]]]]]]]]]]"


  "JenkinsObject Json reads" should:
    "simple pipeline" in:
      val jsonString: JsValue =
          Json.parse(
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
          }}"""
        )

      Json.fromJson[JenkinsConnector.JenkinsObject](jsonString) shouldBe JsSuccess(
        JenkinsConnector.JenkinsObject.WorkflowJob(
          name       = "Pipeline",
          jenkinsUrl = "https://...")
      )

    "simple job" in:
      val jsonString: JsValue =
        Json.parse(
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
            },
            "scm": {
              "_class": "hudson.plugins.git.GitSCM",
              "userRemoteConfigs": [
                {
                  "url": "https://github.com/hmrc/project.git"
                }
              ]
            }}"""
        )

      Json.fromJson[JenkinsConnector.JenkinsObject](jsonString) shouldBe JsSuccess(
        JenkinsConnector.JenkinsObject.FreeStyleProject(
          name        = "job",
          jenkinsUrl  = "https://.../",
          latestBuild = Some(
            JenkinsConnector.LatestBuild(
              number      = 3,
              url         = "https://.../3/",
              timestamp   = Instant.ofEpochMilli(1658499777096L),
              result      = Some(JenkinsConnector.LatestBuild.BuildResult.Success),
              description = None
            )
          ),
          gitHubUrl = Some("https://github.com/hmrc/project.git")
        )
      )

    "simple folder" in:
      val jsonString: JsValue =
          Json.parse(
          """{
          "_class": "com.cloudbees.hudson.plugins.folder.Folder",
          "description": "This folder contains stuff",
          "fullName": "Folder",
          "name": "Folder",
          "url": "https://...",
         "jobs" : []}"""
        )

      Json.fromJson[JenkinsConnector.JenkinsObject](jsonString) shouldBe JsSuccess(
        JenkinsConnector.JenkinsObject.Folder(
          name       = "Folder",
          jenkinsUrl = "https://...",
          jobs       = Seq.empty[JenkinsConnector.JenkinsObject]
        )
      )

    "Folder with a job" in:
      val jsonString: JsValue =
        Json.parse(
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
              }]}"""
        )

      Json.fromJson[JenkinsConnector.JenkinsObject](jsonString) shouldBe JsSuccess(
        JenkinsConnector.JenkinsObject.Folder(
          name       = "Folder",
          jenkinsUrl = "https://...",
          jobs       = Seq(JenkinsConnector.JenkinsObject.WorkflowJob("Job", "https://..."))
        )
      )
