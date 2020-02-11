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
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.connectors.{GithubConnector, RateLimitMetrics}
import uk.gov.hmrc.metrix.MetricOrchestrator
import uk.gov.hmrc.metrix.domain.MetricSource
import uk.gov.hmrc.metrix.persistence.MongoMetricRepository

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class GithubRatelimitMetricsScheduler @Inject()(
     githubConnector: GithubConnector
   , config         : SchedulerConfigs
   , mongoLocks     : MongoLocks
   , metrics        : Metrics
   , mongo          : ReactiveMongoComponent
   )( implicit
      actorSystem         : ActorSystem
    , applicationLifecycle: ApplicationLifecycle
    , ec                  : ExecutionContext
    ) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val source: MetricSource =
    new MetricSource {
      def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
        for {
          remaining <- githubConnector.getRateLimitMetrics.map(_.remaining)
        } yield Map("github.ratelimit.remaining" -> remaining)
    }

  val metricOrchestrator = new MetricOrchestrator(
    metricSources     = List(source)
  , lock              = mongoLocks.metrixLock
  , metricRepository  = new MongoMetricRepository()(mongo.mongoConnector.db)
  , metricRegistry    = metrics.defaultRegistry
  )

  schedule("Github Ratelimit metrics", config.metrixScheduler) {
    metricOrchestrator.attemptToUpdateAndRefreshMetrics()
      .map(_ => ())
  }
}
