/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.metrix.{MetricOrchestrator, MetricSource, MongoMetricRepository}
import uk.gov.hmrc.teamsandrepositories.config.{GithubConfig, SchedulerConfigs}
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persistence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GithubRatelimitMetricsScheduler @Inject()(
  githubConnector: GithubConnector,
  schedulerConfig: SchedulerConfigs,
  githubConfig   : GithubConfig,
  mongoLocks     : MongoLocks,
  metrics        : Metrics,
  mongoComponent : MongoComponent
)(implicit
  actorSystem         : ActorSystem,
  applicationLifecycle: ApplicationLifecycle
) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val ec: ExecutionContext = actorSystem.dispatchers.lookup("scheduler-dispatcher")

  val metricsDefinitions: Map[String, () => Future[Int]] =
    githubConfig.tokens
      .map { case (username, token) =>
        s"github.token.$username.rate.remaining" -> { () =>
          githubConnector.getRateLimitMetrics(token).map(_.remaining)
        }
      }.toMap

  val source: MetricSource =
    new MetricSource {
      def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
        metricsDefinitions.toList.traverse { case (k, f) => f().map(i => (k, i)) }.map(_.toMap)
    }

  val metricOrchestrator = new MetricOrchestrator(
    metricSources    = List(source),
    lockService      = mongoLocks.metrixLock,
    metricRepository = new MongoMetricRepository(mongoComponent),
    metricRegistry   = metrics.defaultRegistry
  )

  schedule("Github Ratelimit metrics", schedulerConfig.metrixScheduler) {
    metricOrchestrator
      .attemptMetricRefresh()
      .map(_ => ())
  }
}
