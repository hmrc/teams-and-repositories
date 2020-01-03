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

package uk.gov.hmrc.teamsandrepositories.controller.test
import org.scalatest.WordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

class IntegrationTestSupportControllerTest extends WordSpec {

  "Integration Controller" should {

    "Read json" in {
      val json = """[{
                   |    "teamName" : "PlatOps",
                   |    "repositories" : [
                   |        {
                   |            "name" : "catalogue-frontend",
                   |            "description" : "",
                   |            "url" : "https://github.com/hmrc/catalogue-frontend",
                   |            "createdDate" : 1456326530000,
                   |            "lastActiveDate" : 1541071695000,
                   |            "isPrivate" : false,
                   |            "repoType" : "Service",
                   |            "digitalServiceName" : "Catalogue",
                   |            "owningTeams" : [],
                   |            "language" : "CSS"
                   |        }
                   |    ],
                   |    "updateDate" : 1542202914078
                   |}]""".stripMargin


      import TeamRepositories.formats

       Json.parse(json).validate[Seq[TeamRepositories]] match {
        case JsSuccess(v: Seq[TeamRepositories], _) => v.foreach(println)
        case JsError(_) => println("not found")
      }


    }




  }





}
