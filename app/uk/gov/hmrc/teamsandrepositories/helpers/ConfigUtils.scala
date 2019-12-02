package uk.gov.hmrc.teamsandrepositories.helpers

import play.api.Configuration

import scala.concurrent.duration.{DurationLong, FiniteDuration}

trait ConfigUtils {
  def getOptDuration(configuration: Configuration, key: String): Option[FiniteDuration] =
    Option(configuration.getMillis(key))
      .map(_.milliseconds)

  def getDuration(configuration: Configuration, key: String): FiniteDuration =
    getOptDuration(configuration, key)
      .getOrElse(sys.error(s"$key not specified"))
}

object ConfigUtils extends ConfigUtils
