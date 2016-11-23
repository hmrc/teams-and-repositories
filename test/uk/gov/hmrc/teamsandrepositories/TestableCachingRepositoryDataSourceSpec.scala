/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, TestData, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.teamsandrepositories.DataGetter.DataLoaderFunction
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.Success


class TestableCachingRepositoryDataSourceSpec extends WordSpec
  with BeforeAndAfterAll
  with ScalaFutures
  with Matchers
  with DefaultPatienceConfig
  with MockitoSugar
  with Eventually
  with OneAppPerTest {


  override def newAppForTest(testData: TestData) = new GuiceApplicationBuilder()
      .configure(
        Map(
        "github.open.api.host" ->           "http://bla.bla",
        "github.open.api.user" ->           "",
        "github.open.api.key" ->            "",
        "github.enterprise.api.host" ->     "http://bla.bla",
        "github.enterprise.api.user" ->     "",
        "github.enterprise.api.key" ->      ""
        )
      )
    .disable(classOf[com.kenshoo.play.metrics.PlayModule]).build()

  //!@ can we remove this?
  val testConfig = new CacheConfig(mock[Configuration]) {
    override def teamsCacheDuration: FiniteDuration = FiniteDuration(100, TimeUnit.SECONDS)
  }

  def withCache[T](dataGetter:DataGetter[T], testConfig:CacheConfig = testConfig)(block: (MemoryCachedRepositoryDataSource[T]) => Unit): Unit ={
    val cache = new MemoryCachedRepositoryDataSource(dataGetter, () => LocalDateTime.now())
    block(cache)
  }

  "Caching teams repository data source" should {

    "return an uncompleted future when called before the cache has been populated" in {
      val promise1 = Promise[Seq[String]]()

      val testDataGetter = new DataGetter[String] {
        override val runner: () => Future[Seq[String]] = () => promise1.future
      }

      withCache[String](testDataGetter){ cache =>
        cache.getCachedTeamRepoMapping.isCompleted shouldBe false
      }
    }

    "return the current result when the cache is in the process of reloading" in {
      val (promise1, promise2) = (Promise[Seq[String]](), Promise[Seq[String]]())
      val ResultValue = Seq("ResultValue")

      val cachedData = Iterator[Promise[Seq[String]]](promise1, promise2).map(_.future)

      val testDataGetter = new DataGetter[String] {
        override val runner: () => Future[Seq[String]] = () => cachedData.next
      }

      withCache(testDataGetter) { cache =>
        promise1.complete(Success(ResultValue))

        eventually {
          cache.getCachedTeamRepoMapping.futureValue.data shouldBe ResultValue
        }

        cache.reload()

        cache.getCachedTeamRepoMapping.isCompleted shouldBe true
        cache.getCachedTeamRepoMapping.futureValue.data shouldBe ResultValue
      }
    }


    "return the updated result when the cache has completed reloading" in {
      val (promise1, promise2) = (Promise[Seq[String]](), Promise[Seq[String]]())

      val cachedData = Iterator[Promise[Seq[String]]](promise1, promise2).map(_.future)

      val testDataGetter = new DataGetter[String] {
        override val runner: () => Future[Seq[String]] = () => cachedData.next
      }

      withCache(testDataGetter) { cache =>
        promise1.success(Seq("result1"))

        eventually {
          cache.getCachedTeamRepoMapping.isCompleted shouldBe true
        }

        cache.reload()

        promise2.success(Seq("result2"))

        eventually {
          cache.getCachedTeamRepoMapping.futureValue.data shouldBe Seq("result2")
        }
      }
    }


    "return a completed future when the cache has been populated" in {

      val (promise1, promise2) = (Promise[Seq[String]](), Promise[Seq[String]]())

      val cachedData = Iterator[Promise[Seq[String]]](promise1, promise2).map(_.future)

      val testDataGetter = new DataGetter[String] {
        override val runner: () => Future[Seq[String]] = () => cachedData.next
      }

      withCache(testDataGetter) { cache =>

        val future1 = cache.getCachedTeamRepoMapping
        future1.isCompleted shouldBe false
        promise1.complete(Success(Seq("result1")))
        eventually {
          future1.futureValue.data shouldBe Seq("result1")
        }
      }
    }

  }
}
