package uk.gov.hmrc.catalogue.github

trait GithubEndpoints {
  def rootUrl: String

  def reposForTeamEndpoint(teamId: Long): String

  def teamsForOrganisationEndpoint(organisation: String): String

  def organisationsEndpoint: String
}

trait GithubEnterpriseApiEndpoints extends GithubEndpoints {
  override def rootUrl: String = "http://example.com"

  def reposForTeamEndpoint(teamId: Long) = s"/api/v3/teams/$teamId/repos?per_page=100"

  def teamsForOrganisationEndpoint(organisation: String) = s"/api/v3/orgs/$organisation/teams?per_page=100"

  def organisationsEndpoint = "/api/v3/user/orgs"
}

trait GithubOpenApiEndpoints extends GithubEndpoints {
  override def rootUrl: String = "https://api.github.com"

  def reposForTeamEndpoint(teamId: Long) = s"/teams/$teamId/repos?per_page=100"

  def teamsForOrganisationEndpoint(organisation: String) = s"/orgs/$organisation/teams?per_page=100"

  def organisationsEndpoint = "/user/orgs"
}