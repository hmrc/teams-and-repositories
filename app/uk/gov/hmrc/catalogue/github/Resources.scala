package uk.gov.hmrc.catalogue.github

import play.api.libs.json.Json

case class GhOrganization(login: String, id: Int = 0)

case class GhRepository(name: String, id: Long, html_url: String)

case class GhTeam(name: String, id: Long)

object GhTeam {
  implicit val formats = Json.format[GhTeam]
}

object GhOrganization {
  implicit val formats = Json.format[GhOrganization]
}

object GhRepository {
  implicit val formats = Json.format[GhRepository]
}