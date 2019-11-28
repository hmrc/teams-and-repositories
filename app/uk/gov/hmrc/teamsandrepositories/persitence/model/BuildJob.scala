package uk.gov.hmrc.teamsandrepositories.persitence.model

import play.api.libs.json._

case class BuildJob(service: String, jenkinsURL: String)

object BuildJob {
  implicit val formats: OFormat[BuildJob] =
    Json.format[BuildJob]

  implicit val apiWriter: Writes[BuildJob] = new Writes[BuildJob] {
    override def writes(o: BuildJob): JsValue = Json.obj("service" -> o.service, "jenkinsURL" -> o.jenkinsURL)
  }

}
