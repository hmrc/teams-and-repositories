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
import com.google.inject.AbstractModule
import com.typesafe.config.Config
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logger}
import play.api.libs.ws.{WSClient, WSProxyServer}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy, WSProxyConfiguration}

class Module() extends AbstractModule {

  private val logger = Logger(this.getClass)

  override def configure(): Unit = {

    logger.info(s"APPLICATION-HOME=${sys.env.getOrElse("application.home", "NOT SET")}")
    logger.info(s"USER-HOME=${sys.props.getOrElse("user.home", "NOT SET")}")
    logger.info(s"USER-DIR=${sys.props.getOrElse("user.dir", "NOT SET")}")
    logger.info(s"ALL-ENV-KEYS=[${sys.env.keySet.mkString(",")}]")

    bind(classOf[DataReloadScheduler]).asEagerSingleton()
    bind(classOf[JenkinsScheduler]).asEagerSingleton()
    bind(classOf[GithubRatelimitMetricsScheduler]).asEagerSingleton()
  }
}

class HttpClientModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
  }
}

@Singleton
class DefaultHttpClient @Inject()(
  config: Configuration,
  val httpAuditing: HttpAuditing,
  override val wsClient: WSClient,
  override protected val actorSystem: ActorSystem
) extends uk.gov.hmrc.http.HttpClient with WSHttp with WSProxy {

  override lazy val configuration: Option[Config] = Option(config.underlying)

  override val hooks: Seq[HttpHook] = Seq(httpAuditing.AuditingHook)

  override def wsProxyServer: Option[WSProxyServer] =
    WSProxyConfiguration(configPrefix = "proxy", configuration = config)
}
