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

package uk.gov.hmrc.teamsandrepositories.connector

import play.api.Logging
import play.api.libs.json.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.teamsandrepositories.model.User

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(using
  ExecutionContext
) extends Logging:

  import HttpReads.Implicits.*

  private given HeaderCarrier = HeaderCarrier()

  private val baseUrl = servicesConfig.baseUrl("user-management")

  def getUsersForTeam(team: String): Future[Seq[User]] =
    val url: URL = url"$baseUrl/user-management/users?team=$team"
    given Reads[User] = User.reads
    httpClientV2
      .get(url)
      .execute[Seq[User]]
