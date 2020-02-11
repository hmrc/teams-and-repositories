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

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import com.kenshoo.play.metrics.Metrics
import com.codahale.metrics.Gauge
import uk.gov.hmrc.teamsandrepositories.config.SchedulerConfigs
import uk.gov.hmrc.teamsandrepositories.helpers.SchedulerUtils
import uk.gov.hmrc.teamsandrepositories.persitence.MongoLocks
import uk.gov.hmrc.teamsandrepositories.connectors.{GithubConnector, RateLimitMetrics}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GithubRatelimitMetricsScheduler @Inject()(
     githubConnector: GithubConnector
   , config         : SchedulerConfigs
   , mongoLocks     : MongoLocks
   , metrics        : Metrics
   )( implicit
      actorSystem         : ActorSystem
    , applicationLifecycle: ApplicationLifecycle
    ) extends SchedulerUtils {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  import ExecutionContext.Implicits.global

  val atomicRef = new AtomicReference[Option[RateLimitMetrics]]()
  metrics.defaultRegistry.register(s"github.ratelimit.remaining", RateLimitGuage(atomicRef))

  scheduleWithLock("Github Ratelimit metrics", config.dataReloadScheduler, mongoLocks.dataReloadLock) {
    for {
      rateLimitMetrics <- githubConnector.getRateLimitMetrics
    } yield atomicRef.set(Some(rateLimitMetrics))
  }
}

case class RateLimitGuage(atomicRef: AtomicReference[Option[RateLimitMetrics]]) extends Gauge[Int] {
  override def getValue: Int =
    atomicRef.get().map(_.remaining).getOrElse(0)
}
