package uk.gov.hmrc.teamsandrepositories.config

import javax.inject.Inject
import play.api.Configuration
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.duration.FiniteDuration

class JenkinsConfig @Inject()(config: Configuration) {
  lazy val username: String = config.getOptional[String]("jenkins.username").getOrElse(
    throw new RuntimeException("Error getting config value jenkins.username"))

  lazy val password: String = config.getOptional[String]("jenkins.password").getOrElse(
    throw new RuntimeException("Error getting config value jenkins.password"))

  lazy val baseUrl: String = config.getOptional[String]("jenkins.url").getOrElse(
    throw new RuntimeException("Error getting config value jenkins.url")
  )

  private val defaultTimeout = 2 hours

  def getDuration(key: String): Option[FiniteDuration] =
    config.getOptional[FiniteDuration](key)

  def reloadEnabled: Boolean = config.getOptional[Boolean]("jenkins.reloadEnabled").getOrElse(false)

  def initialDelay: FiniteDuration = getDuration("jenkins.initialDelay").getOrElse(5 seconds)

  def reloadDuration: FiniteDuration = getDuration("jenkins.reloadDuration").getOrElse(defaultTimeout)
}
