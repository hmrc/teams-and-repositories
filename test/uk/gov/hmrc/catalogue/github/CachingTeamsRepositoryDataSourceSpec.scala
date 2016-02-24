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

package uk.gov.hmrc.catalogue.github

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.test.WithApplication
import uk.gov.hmrc.catalogue.DefaultPatienceConfig
import uk.gov.hmrc.catalogue.config.{CacheConfig, CacheConfigProvider}
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, Team}
import uk.gov.hmrc.catalogue.teams.{CachingTeamsRepositoryDataSource, TeamsRepositoryDataSource}

import scala.concurrent.Future
import scala.concurrent.duration._

class CachingTeamsRepositoryDataSourceSpec extends WordSpec with MockitoSugar with ScalaFutures with Matchers with DefaultPatienceConfig with Eventually {

  var cacheSource: Team = null
  val team1 = Team("test", List(Repository("test_repo", "test_repo_url")))
  val team2 = Team("test2", List(Repository("test_repo", "test_repo_url")))

  val cacheTimeout = 10 millis

  val dataSource = new TeamsRepositoryDataSource {
    override def getTeamRepoMapping: Future[List[Team]] = Future.successful(List(cacheSource))
  }

  "Caching teams repository data source" should {

    "populate the cache from the data source and retain it until the configured expiry time" in new WithApplication {

      val cachingDataSource = new CachingTeamsRepositoryDataSource(dataSource) with DummyCacheConfigProvider

      cacheSource = team1
      verifyCacheHasBeenPopulatedWith(cachingDataSource, team1)

      cacheSource = team2
      verifyCachedCopyIsStill(cachingDataSource, team1)
      verifyCacheIsRefreshedAfterConfiguredTimeoutWith(cachingDataSource, team2)

    }

    def verifyCacheHasBeenPopulatedWith(cache: CachingTeamsRepositoryDataSource, team: Team) =
      eventually { cache.getTeamRepoMapping.futureValue should contain(team) }

    def verifyCachedCopyIsStill(cache: CachingTeamsRepositoryDataSource, team: Team) =
      cache.getTeamRepoMapping.futureValue should contain (team)

    def verifyCacheIsRefreshedAfterConfiguredTimeoutWith(cache: CachingTeamsRepositoryDataSource, team: Team) =
      eventually { cache.getTeamRepoMapping.futureValue should contain (team) }

    trait DummyCacheConfigProvider extends CacheConfigProvider {
      override def cacheConfig: CacheConfig = new CacheConfig {
        override def teamsCacheDuration: FiniteDuration = cacheTimeout
      }
    }
  }
}
