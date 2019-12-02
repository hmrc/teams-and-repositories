package uk.gov.hmrc.teamsandrepositories.persitence.model

import play.api.libs.json._

case class BuildJob(service: String, jenkinsURL: String)

object BuildJob {
  val mongoFormats: OFormat[BuildJob] =
    Json.format[BuildJob]

  val apiWriter: Writes[BuildJob] = new Writes[BuildJob] {
    override def writes(o: BuildJob): JsValue = Json.obj("service" -> o.service, "jenkinsURL" -> o.jenkinsURL)
  }

}
