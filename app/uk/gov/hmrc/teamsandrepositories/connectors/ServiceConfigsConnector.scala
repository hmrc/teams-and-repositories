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

package uk.gov.hmrc.teamsandrepositories.connectors

import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ServiceConfigsConnector @Inject()(
                                           servicesConfig: ServicesConfig,
                                           httpClientV2: HttpClientV2
                                         ){

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val url = servicesConfig.baseUrl("service-configs")

  def getFrontendServices()(implicit ec: ExecutionContext): Future[Set[String]] = {
    httpClientV2
      .get(url"$url/service-configs/frontend-services")
      .execute[Set[String]]
  }
}

