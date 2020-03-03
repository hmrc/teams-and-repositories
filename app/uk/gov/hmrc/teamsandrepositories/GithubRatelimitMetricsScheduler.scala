/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.actor.ActorSystem
import cats.implicits._
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.metrix.MetricOrchestrator
import uk.gov.hmrc.metrix.domain.MetricSource
import uk.gov.hmrc.metrix.persistence.MongoMetricRepository
import uk.gov.hmrc.teamsandrepositories.config.{GithubConfig, SchedulerConfigs}
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.connectors.{GithubConnector, RateLimitMetrics}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class GithubRatelimitMetricsScheduler @Inject()(
     githubConnector: GithubConnector
   , schedulerConfig: SchedulerConfigs
   , githubConfig   : GithubConfig
   , mongoLocks     : MongoLocks
   , metrics        : Metrics
   , mongo          : ReactiveMongoComponent
   )( implicit
      actorSystem         : ActorSystem
    , applicationLifecycle: ApplicationLifecycle
    , ec                  : ExecutionContext
    ) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()



  val metricsDefinitions: Map[String, () => Future[Int]] = {
    githubConfig.tokens.map { case (username, token) =>
      s"github.token.${username}.rate.remaining" ->
        {() => githubConnector.getRateLimitMetrics(token).map(_.remaining)}
    }.toMap
  }

  val source: MetricSource =
    new MetricSource {
      def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
        metricsDefinitions.toList.traverse { case (k, f) => f().map(i => (k, i)) }.map(_.toMap)
    }

  val metricOrchestrator = new MetricOrchestrator(
    metricSources     = List(source)
  , lock              = mongoLocks.metrixLock
  , metricRepository  = new MongoMetricRepository()(mongo.mongoConnector.db)
  , metricRegistry    = metrics.defaultRegistry
  )

  schedule("Github Ratelimit metrics", schedulerConfig.metrixScheduler) {
    metricOrchestrator
      .attemptToUpdateAndRefreshMetrics(
        skipReportingOn = persistedMetric => !metricsDefinitions.contains(persistedMetric.name) // it would be better if old metrics were deleted from store...
      )
      .map(_ => ())
  }
}
