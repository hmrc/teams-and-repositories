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
