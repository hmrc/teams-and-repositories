package uk.gov.hmrc.catalogue

import org.joda.time.DateTime
import play.api.libs.json._

class CachedList[T](val data: Seq[T], val time: DateTime) extends Seq[T] {
  override def length: Int = data.length
  override def apply(idx: Int): T = data.apply(idx)
  override def iterator: Iterator[T] = data.iterator
}

object CachedList {
  import play.api.libs.functional.syntax._

  implicit def cachedListFormats[T](implicit fmt: Format[T]): Format[CachedList[T]] = new Format[CachedList[T]] {
    private val writes: Writes[CachedList[T]] = (
      (JsPath \ "data").write[Seq[T]] and
        (JsPath \ "cacheTimestamp").write[DateTime]
      ) (x => (x.data, x.time))

    override def writes(o: CachedList[T]): JsValue = writes.writes(o)
    override def reads(json: JsValue): JsResult[CachedList[T]] = ???
  }
}