package uk.gov.hmrc.teamsandrepositories.testonly

import javax.inject.Inject
import play.api.libs.json.{JsError, Reads}
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.TeamsAndReposPersister
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IntegrationTestSupportController @Inject()(repo: TeamsAndReposPersister,
                                                 futureHelpers: FutureHelpers,
                                                 cc: ControllerComponents) extends BackendController(cc) {

  def validateJson[A : Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e))))

  def addTeams() = Action.async(validateJson[Seq[TeamRepositories]]) { implicit request =>
    Future.sequence( request.body.map(repo.update) ).map { _ => Ok("Done") }
  }

  def clearAll() = Action.async { implicit  request =>
     repo.clearAllData().map(_=> Ok("Ok"))
  }

}
