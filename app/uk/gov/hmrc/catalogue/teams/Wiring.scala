/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.catalogue.teams

import uk.gov.hmrc.catalogue.github._

trait CompositeTeamsRepositoryDataSourceProvider extends TeamsRepositoryDataSourceProvider with GithubEnterpriseDataSource with GithubOpenDataSource {
  val dataSource: TeamsRepositoryDataSource = new CompositeTeamsRepositoryDataSource(List(enterpriseDataSource, openDataSource))
}

trait GithubEnterpriseDataSource {
  private val httpClient = new GithubV3ApiClient with GithubEnterpriseApiEndpoints with GithubEnterpriseCredentialsProvider
  val enterpriseDataSource: TeamsRepositoryDataSource = new GithubV3TeamsRepositoryDataSource(httpClient)
}

trait GithubOpenDataSource {
  private val httpClient = new GithubV3ApiClient with GithubOpenApiEndpoints  with GithubOpenCredentialsProvider
  val openDataSource: TeamsRepositoryDataSource = new GithubV3TeamsRepositoryDataSource(httpClient)
}
