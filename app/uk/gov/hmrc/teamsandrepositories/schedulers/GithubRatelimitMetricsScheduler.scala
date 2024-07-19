/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.schedulers

import org.apache.pekko.actor.ActorSystem
import cats.implicits._
import com.codahale.metrics.MetricRegistry
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.mongo.metrix.{MetricOrchestrator, MetricSource, MongoMetricRepository}
import uk.gov.hmrc.teamsandrepositories.config.{GithubConfig, SchedulerConfigs}
import uk.gov.hmrc.teamsandrepositories.connectors.{GithubConnector, RateLimitMetrics}
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GithubRatelimitMetricsScheduler @Inject()(
  githubConnector    : GithubConnector,
  schedulerConfig    : SchedulerConfigs,
  githubConfig       : GithubConfig,
  metricRegistry     : MetricRegistry,
  mongoComponent     : MongoComponent,
  mongoLockRepository: MongoLockRepository
)(using
  actorSystem         : ActorSystem,
  applicationLifecycle: ApplicationLifecycle
) extends SchedulerUtils:

  given HeaderCarrier = HeaderCarrier()

  given ExecutionContext = actorSystem.dispatchers.lookup("scheduler-dispatcher")

  private val metricsDefinitions: Map[String, () => Future[Int]] =
    import RateLimitMetrics.Resource._

    githubConfig.tokens
      .flatMap:
        case (username, token) =>
          List(
            s"github.token.$username.rate.remaining" -> { () =>
              githubConnector.getRateLimitMetrics(token, Core).map(_.remaining)
            },
            s"github.token.$username.graphql.rate.remaining" -> { () =>
              githubConnector.getRateLimitMetrics(token, GraphQl).map(_.remaining)
            },
          )
      .toMap

  private val source: MetricSource =
    new MetricSource {
      def metrics(using ExecutionContext): Future[Map[String, Int]] =
        metricsDefinitions.toList.traverse { case (k, f) => f().map(i => (k, i)) }.map(_.toMap)
    }

  private val lockService: LockService =
    LockService(
      lockRepository = mongoLockRepository,
      lockId         = "metrix-lock",
      ttl            = 20.minutes
    )

  private val metricOrchestrator: MetricOrchestrator =
    new MetricOrchestrator(
      metricSources    = List(source),
      lockService      = lockService,
      metricRepository = MongoMetricRepository(mongoComponent),
      metricRegistry   = metricRegistry
    )

  schedule("Github Ratelimit metrics", schedulerConfig.metrixScheduler) {
    metricOrchestrator
      .attemptMetricRefresh()
      .map(_ => ())
  }
