package uk.gov.hmrc.teamsandrepositories.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsResult, JsValue, Json}

class BuildJobSpec extends AnyWordSpec with Matchers {

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
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.models.JenkinsPipeline"
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
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.models.BuildJob"
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
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.models.JenkinsFolder"
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
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.models.JenkinsFolder"
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
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.models.JenkinsBuildJobsWrapper"
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
      residentFromJson.get.getClass.getName shouldBe "uk.gov.hmrc.teamsandrepositories.models.JenkinsBuildJobsWrapper"
    }
  }
}
