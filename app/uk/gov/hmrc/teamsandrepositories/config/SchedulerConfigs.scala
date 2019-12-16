package uk.gov.hmrc.teamsandrepositories.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.teamsandrepositories.helpers.ConfigUtils

import scala.concurrent.duration.FiniteDuration

case class SchedulerConfig(
                            enabledKey  : String
                            , enabled     : Boolean
                            , frequency   : () => FiniteDuration
                            , initialDelay: () => FiniteDuration
                          )

object SchedulerConfig {
  import ConfigUtils._

  def apply(
        configuration: Configuration
      , enabledKey   : String
      , frequency    : => FiniteDuration
      , initialDelay : => FiniteDuration
      ): SchedulerConfig =
    SchedulerConfig(
        enabledKey
      , enabled      = configuration.get[Boolean](enabledKey)
      , frequency    = () => frequency
      , initialDelay = () => initialDelay
      )

  def apply(
        configuration  : Configuration
      , enabledKey     : String
      , frequencyKey   : String
      , initialDelayKey: String
      ): SchedulerConfig =
    SchedulerConfig(
        enabledKey
      , enabled      = configuration.get[Boolean](enabledKey)
      , frequency    = () => getDuration(configuration, frequencyKey)
      , initialDelay = () => getDuration(configuration, initialDelayKey)
      )
}

@Singleton
class SchedulerConfigs @Inject()(configuration: Configuration) extends ConfigUtils {

  val jenkinsScheduler = SchedulerConfig(
      configuration
    , enabledKey      = "cache.jenkins.reloadEnabled"
    , frequencyKey    = "cache.jenkins.duration"
    , initialDelayKey = "cache.jenkins.initialDelay"
    )

  val dataReloadScheduler = SchedulerConfig(
      configuration
    , enabledKey      = "cache.teams.reloadEnabled"
    , frequencyKey    = "cache.teams.initialDelay"
    , initialDelayKey = "cache.teams.duration"
    )
}
