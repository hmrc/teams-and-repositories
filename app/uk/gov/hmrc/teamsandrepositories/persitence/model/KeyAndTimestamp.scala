package uk.gov.hmrc.teamsandrepositories.persitence.model

import java.time.{LocalDateTime, ZoneOffset}

import play.api.libs.json._

case class KeyAndTimestamp(keyName: String, timestamp: LocalDateTime)

object KeyAndTimestamp {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats = Json.format[KeyAndTimestamp]
}