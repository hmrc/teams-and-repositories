package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.scalatest.concurrent.ScalaFutures
import org.scalatest._
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MongoUpdateTimePersisterSpec extends WordSpec
with Matchers
  with MongoSpecSupport
  with ScalaFutures
  with BeforeAndAfterEach
  with OptionValues
  with OneAppPerSuite {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder().configure(Map("mongodb.uri" -> "mongodb://localhost:27017/test-teams-and-repositories")).build()

  import scala.concurrent.duration._
  import scala.concurrent.{Await, Future}

  implicit val defaultTimeout = 5 seconds

  implicit def extractAwait[A](future: Future[A]) = await[A](future)

  def await[A](future: Future[A])(implicit timeout: Duration) = Await.result(future, timeout)

  val mongoUpdateTimePersister = app.injector.instanceOf(classOf[MongoUpdateTimePersister])

  override def beforeEach() {
    await(mongoUpdateTimePersister.drop)
  }

  "update" should {
    "update already existing key" in {

      val now: LocalDateTime = LocalDateTime.now()
      val oneHourLater = now.plusHours(1)

      val key = "teamsAndReposUpdateKey"

      await(mongoUpdateTimePersister.update(KeyAndTimestamp(key, now)))

      val updatedKeyAndTimestamp = KeyAndTimestamp(key, oneHourLater)
      await(mongoUpdateTimePersister.update(updatedKeyAndTimestamp))

      val updated = await(mongoUpdateTimePersister.get(key))

      updated.value.timestamp.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) shouldBe oneHourLater.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))

    }

  }
  "remove" should {
    "remove existing key" in {

      val now: LocalDateTime = LocalDateTime.now()

      val key = "teamsAndReposUpdateKey"

      await(mongoUpdateTimePersister.update(KeyAndTimestamp(key, now)))

      val errors = await(mongoUpdateTimePersister.remove(key))


      val removed = await(mongoUpdateTimePersister.get(key))

      removed shouldBe None

    }

  }

}