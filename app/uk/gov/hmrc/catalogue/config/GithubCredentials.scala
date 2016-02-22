package uk.gov.hmrc.catalogue.config

case class GithubCredentials(host: String, user: String, key: String)

trait GithubCredentialsProvider {
  def cred: GithubCredentials
}

trait GithubEnterpriseCredentialsProvider extends GithubCredentialsProvider {
  val cred = ConfigProvider.githubEnterprise
}

trait GithubOpenCredentialsProvider extends GithubCredentialsProvider {
  val cred = ConfigProvider.githubOpen
}