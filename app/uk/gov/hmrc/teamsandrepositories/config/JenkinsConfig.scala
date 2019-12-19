package uk.gov.hmrc.teamsandrepositories.config

import javax.inject.Inject
import play.api.Configuration

import scala.language.postfixOps

class JenkinsConfig @Inject()(config: Configuration) {
  lazy val username: String = config.get[String]("jenkins.username")

  lazy val token: String = config.get[String]("jenkins.token")

  lazy val baseUrl: String = config.get[String]("jenkins.url")
}
