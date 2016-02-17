package uk.gov.hmrc.catalogue.teams

import uk.gov.hmrc.catalogue.github._
import uk.gov.hmrc.catalogue.teamsrepository.{GithubV3TeamsRepositoryDataSource, TeamsRepositoryDataSource}

trait GithubEnterpriseDataSource {
  private val httpClient = new GithubV3ApiClient with GithubEnterpriseApiEndpoints with StoredCredentials
  val enterpriseDataSource: TeamsRepositoryDataSource = new GithubV3TeamsRepositoryDataSource(httpClient)
}

trait GithubOpenDataSource {
  private val httpClient = new GithubV3ApiClient with GithubOpenApiEndpoints with StoredCredentials
  val openDataSource: TeamsRepositoryDataSource = new GithubV3TeamsRepositoryDataSource(httpClient)
}
