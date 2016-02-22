package uk.gov.hmrc.catalogue.config

import java.io.File

import play.api.Play
import uk.gov.hmrc.play.config.RunMode

trait ConfigProvider {
  val githubOpen: GithubCredentials
  val githubEnterprise: GithubCredentials
}

object ConfigProvider extends ConfigProvider with CredentialsFinder with RunMode {
  val githubOpenConfigKey = s"${RunMode.env}.github.open.api"
  val githubEnterpriseConfigKey = s"${RunMode.env}.github.enterprise.api"

  val githubOpen = fallBackToFileSystem(".credentials", GithubCredentials(
    config(s"$githubOpenConfigKey.host"),
    config(s"$githubOpenConfigKey.user"),
    config(s"$githubOpenConfigKey.key")))

  val githubEnterprise = fallBackToFileSystem(".githubenterprise", GithubCredentials(
    config(s"$githubOpenConfigKey.host"),
    config(s"$githubEnterpriseConfigKey.user"),
    config(s"$githubEnterpriseConfigKey.key")))

  private def fallBackToFileSystem(filename: String, credentials: GithubCredentials) = {
    def isNullOrEmpty(s: String) = s != null && s.isEmpty

    if (isNullOrEmpty(credentials.host) || isNullOrEmpty(credentials.user)) {
      new File(System.getProperty("user.home"), filename).listFiles()
        .flatMap { c => findGithubCredsInFile(c.toPath) }.head
    }

    credentials
  }

  private def config(path: String) = Play.current.configuration.getString(s"${RunMode.env}.$path").getOrElse("")

}
