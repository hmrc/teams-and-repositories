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

package uk.gov.hmrc.catalogue.teams

import org.joda.time.DateTime
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.test.WithApplication
import uk.gov.hmrc.catalogue.DefaultPatienceConfig
import uk.gov.hmrc.catalogue.config.{CacheConfig, CacheConfigProvider}
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, TeamRepositories}

import scala.collection.mutable.Stack
import scala.concurrent.Future
import scala.concurrent.duration._

class CachingTeamsRepositoryDataSourceSpec extends WordSpec with MockitoSugar with ScalaFutures with Matchers with DefaultPatienceConfig with Eventually {

  var cacheSource: TeamRepositories = null
  val team1 = TeamRepositories("test", List(Repository("test_repo", "test_repo_url")))
  val team2 = TeamRepositories("test2", List(Repository("test_repo", "test_repo_url")))

  val cacheTimeout = 10 millis
  val longCacheTimeout = 1 minute

  val dataSource = new TeamsRepositoryDataSource {
    override def getTeamRepoMapping: Future[Seq[TeamRepositories]] = Future.successful(List(cacheSource))
  }

  "Caching teams repository data source" should {
    val firstTime = new DateTime(2016, 4, 5, 12, 57)
    val secondTime = new DateTime(2016, 4, 6, 21, 0)

    "populate the cache from the data source and retain it until the configured expiry time" in new WithApplication {
      cacheSource = team1
      val cacheTime = Stack(firstTime, secondTime)
      val cachingDataSource = new CachingTeamsRepositoryDataSource(dataSource, () => cacheTime.pop) with ShortCacheConfigProvider

      verifyCacheHasBeenPopulatedWith(cachingDataSource, team1)
      verifyCacheTime(cachingDataSource, firstTime)

      cacheSource = team2
      verifyCachedCopyIsStill(cachingDataSource, team1)
      verifyCacheTime(cachingDataSource, firstTime)

      verifyCacheIsRefreshed(cachingDataSource, team2)
      verifyCacheTime(cachingDataSource, secondTime)
    }

    "reload the cache from the data source when cleared" in new WithApplication {
      cacheSource = team1
      val cacheTime = Stack(firstTime, secondTime)
      val cachingDataSource = new CachingTeamsRepositoryDataSource(dataSource, () => cacheTime.pop) with LongCacheConfigProvider

      verifyCacheHasBeenPopulatedWith(cachingDataSource, team1)
      verifyCacheTime(cachingDataSource, firstTime)

      cacheSource = team2
      verifyCachedCopyIsStill(cachingDataSource, team1)
      verifyCacheTime(cachingDataSource, firstTime)

      cachingDataSource.reload()
      verifyCacheIsRefreshed(cachingDataSource, team2)
      verifyCacheTime(cachingDataSource, secondTime)
    }

    def verifyCacheHasBeenPopulatedWith(cache: CachingTeamsRepositoryDataSource, team: TeamRepositories) =
      eventually {
        cache.getCachedTeamRepoMapping.futureValue.data should contain(team)
      }

    def verifyCacheTime(cache: CachingTeamsRepositoryDataSource, dateTime: DateTime) =
      cache.getCachedTeamRepoMapping.futureValue.time should be (dateTime)

    def verifyCachedCopyIsStill(cache: CachingTeamsRepositoryDataSource, team: TeamRepositories) =
      cache.getCachedTeamRepoMapping.futureValue.data should contain (team)

    def verifyCacheIsRefreshed(cache: CachingTeamsRepositoryDataSource, team: TeamRepositories) =
      eventually { cache.getCachedTeamRepoMapping.futureValue.data should contain (team) }

    trait ShortCacheConfigProvider extends CacheConfigProvider {
      override def cacheConfig: CacheConfig = new CacheConfig {
        override def teamsCacheDuration: FiniteDuration = cacheTimeout
      }
    }

    trait LongCacheConfigProvider extends CacheConfigProvider {
      override def cacheConfig: CacheConfig = new CacheConfig {
        override def teamsCacheDuration: FiniteDuration = longCacheTimeout
      }
    }
  }
}
