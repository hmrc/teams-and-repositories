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

import akka.actor.ActorSystem
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import uk.gov.hmrc.teamsandrepositories.config.CacheConfig

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.Success
import scala.concurrent.duration._


class TestableCachingRepositoryDataSourceSpec extends WordSpec with BeforeAndAfterAll with ScalaFutures with Matchers with DefaultPatienceConfig with Eventually {

  val system = ActorSystem.create()

  override def afterAll(): Unit = {
    system.shutdown()
  }


  val testConfig = new CacheConfig() {
    override def teamsCacheDuration: FiniteDuration = FiniteDuration(100, TimeUnit.SECONDS)
  }

  def withCache[T](dataLoader:() => Future[T], testConfig:CacheConfig = testConfig)(block: (CachingRepositoryDataSource[T]) => Unit): Unit ={
    val cache = new CachingRepositoryDataSource[T](system, testConfig, dataLoader, () => LocalDateTime.now())
    block(cache)
  }

  "Caching teams repository data source" should {

    "return an uncompleted future when called before the cache has been populated" in {
      val promise1 = Promise[String]()

      withCache(() => promise1.future){ cache =>
        cache.getCachedTeamRepoMapping.isCompleted shouldBe false
      }
    }

    "return the current result when the cache is in the process of reloading" in {
      val (promise1, promise2) = (Promise[String](), Promise[String]())
      val ResultValue = "ResultValue"

      val cachedData = Iterator[Promise[String]](promise1, promise2).map(_.future)

      withCache(() => cachedData.next) { cache =>
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
      val (promise1, promise2) = (Promise[String](), Promise[String]())

      val cachedData = Iterator[Promise[String]](promise1, promise2).map(_.future)

      withCache(() => cachedData.next) { cache =>
        promise1.success("result1")

        eventually {
          cache.getCachedTeamRepoMapping.isCompleted shouldBe true
        }

        cache.reload()

        promise2.success("result2")

        eventually {
          cache.getCachedTeamRepoMapping.futureValue.data shouldBe "result2"
        }
      }
    }


    "return a completed future when the cache has been populated" in {

      val (promise1, promise2) = (Promise[String](), Promise[String]())

      val cachedData = Iterator[Promise[String]](promise1, promise2).map(_.future)

      withCache(() => cachedData.next) { cache =>

        val future1 = cache.getCachedTeamRepoMapping
        future1.isCompleted shouldBe false
        promise1.complete(Success("result1"))
        eventually {
          future1.futureValue.data shouldBe "result1"
        }
      }
    }

    "populate the cache from the data source and retain it until the configured expiry time" in {

      val testConfig = new CacheConfig() {
        override def teamsCacheDuration: FiniteDuration = 100 millis
      }

      val cachedData = Iterator("result1", "result2", "result3").map(Future.successful)

      withCache(() => cachedData.next, testConfig) { cache =>

        eventually {
          cache.getCachedTeamRepoMapping.futureValue.data shouldBe "result1"
        }

        eventually {
          cache.getCachedTeamRepoMapping.futureValue.data shouldBe "result2"
        }
      }
    }
  }
}
