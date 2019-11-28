package uk.gov.hmrc.teamsandrepositories.testonly

import javax.inject.Inject
import play.api.libs.json.{JsError, Reads}
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.helpers.FutureHelpers
import uk.gov.hmrc.teamsandrepositories.persitence.{BuildJobRepo, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.persitence.model.{BuildJob, TeamRepositories}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IntegrationTestSupportController @Inject()(teamsRepo: TeamsAndReposPersister,
                                                 jenkinsRepo: BuildJobRepo,
                                                 futureHelpers: FutureHelpers,
                                                 cc: ControllerComponents) extends BackendController(cc) {

  def validateJson[A : Reads] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e))))

  def addTeams() = Action.async(validateJson[Seq[TeamRepositories]]) { implicit request =>
    Future.sequence( request.body.map(teamsRepo.update) ).map { _ => Ok("Done") }
  }

  def clearAll() = Action.async { implicit  request =>
     teamsRepo.clearAllData.map(_=> Ok("Ok"))
  }

  def addJenkinsLinks() = Action.async(validateJson[Seq[BuildJob]]) { implicit request =>
    jenkinsRepo.update(request.body).map { _ => Ok("Done")}
  }

  def clearJenkins() = Action.async { implicit request =>
    jenkinsRepo.removeAll().map(_ => Ok("Ok"))
  }

}
