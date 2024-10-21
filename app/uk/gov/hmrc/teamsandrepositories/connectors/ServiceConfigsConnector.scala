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

package uk.gov.hmrc.teamsandrepositories.connectors

import play.api.libs.json.{JsValue, Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ServiceConfigsConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
):
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private given HeaderCarrier = HeaderCarrier()

  private val baseUrl = servicesConfig.baseUrl("service-configs")

  private val readsServiceName: Reads[String] =
    (__ \ "serviceName")
      .read[String]
      .map(String.apply)

  def getFrontendServices()(using ExecutionContext): Future[Set[String]] =
    given Reads[String] = readsServiceName
    httpClientV2
      .get(url"$baseUrl/service-configs/routes?routeType=frontend")
      .execute[Set[String]]

  def getAdminFrontendServices()(using ExecutionContext): Future[Set[String]] =
    given Reads[String] = readsServiceName
    httpClientV2
      .get(url"$baseUrl/service-configs/routes?routeType=adminfrontend")
      .execute[Set[String]]

  def hasFrontendRoutes(service: String)(using ExecutionContext): Future[Boolean] =
    httpClientV2
      .get(url"$baseUrl/service-configs/routes?serviceName=$service&routeType=frontend")
      .execute[List[JsValue]]
      .map(_.nonEmpty)

  def hasAdminFrontendRoutes(service: String)(using ExecutionContext): Future[Boolean] =
    httpClientV2
      .get(url"$baseUrl/service-configs/routes?serviceName=$service&routeType=adminfrontend")
      .execute[List[JsValue]]
      .map(_.nonEmpty)
