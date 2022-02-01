/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSProxyServer}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy, WSProxyConfiguration}

class Module() extends AbstractModule {
  override def configure(): Unit = {
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

  override lazy val configuration: Config = config.underlying

  override val hooks: Seq[HttpHook] = Seq(httpAuditing.AuditingHook)

  override def wsProxyServer: Option[WSProxyServer] =
    WSProxyConfiguration(configPrefix = "proxy", configuration = config)
}
