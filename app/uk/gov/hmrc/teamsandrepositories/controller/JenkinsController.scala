package uk.gov.hmrc.teamsandrepositories.controller

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob.apiWriter
import uk.gov.hmrc.teamsandrepositories.services.JenkinsService

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class JenkinsController @Inject()(jenkinsService: JenkinsService, cc: ControllerComponents)
  extends BackendController(cc) {

  def lookup(service: String) = Action.async { implicit request =>
    for {
      findService <- jenkinsService.findByService(service)
      result      =  findService.map(links => Ok(Json.toJson(links))).getOrElse(NoContent)
    } yield result

  }
}
