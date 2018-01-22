package uk.gov.hmrc.teamsandrepositories.controller.model

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import uk.gov.hmrc.teamsandrepositories.RepoType

case class Repository(
  name: String,
  createdAt: Long,
  lastUpdatedAt: Long,
  repoType: RepoType.RepoType,
  language: Option[String])

object Repository {
  implicit val repoDetailsFormat = Json.format[Repository]
}

case class Team(
  name: String,
  firstActiveDate: Option[Long]          = None,
  lastActiveDate: Option[Long]           = None,
  firstServiceCreationDate: Option[Long] = None,
  repos: Option[Map[RepoType.Value, Seq[String]]])

object Team {

  implicit val mapReads: Reads[Map[RepoType.RepoType, Seq[String]]] = new Reads[Map[RepoType.RepoType, Seq[String]]] {
    def reads(jv: JsValue): JsResult[Map[RepoType.RepoType, Seq[String]]] =
      JsSuccess(jv.as[Map[String, Seq[String]]].map {
        case (k, v) =>
          RepoType.withName(k) -> v.asInstanceOf[Seq[String]]
      })
  }

  implicit val mapWrites: Writes[Map[RepoType.RepoType, Seq[String]]] =
    new Writes[Map[RepoType.RepoType, Seq[String]]] {
      def writes(map: Map[RepoType.RepoType, Seq[String]]): JsValue =
        Json.obj(map.map {
          case (s, o) =>
            val ret: (String, JsValueWrapper) = s.toString -> JsArray(o.map(JsString))

            ret
        }.toSeq: _*)
    }

  implicit val mapFormat: Format[Map[RepoType.RepoType, Seq[String]]] = Format(mapReads, mapWrites)

  implicit val format = Json.format[Team]
}
