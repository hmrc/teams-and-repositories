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

package uk.gov.hmrc.teamsandrepositories.service

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.teamsandrepositories.config.{JenkinsConfig, SlackConfig}
import uk.gov.hmrc.teamsandrepositories.connector.{ChannelLookup, JenkinsConnector, SlackNotificationRequest, SlackNotificationsConnector}
import uk.gov.hmrc.teamsandrepositories.persistence.JenkinsJobsPersistence

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class JenkinsRebuildService @Inject()(
  configuration               : Configuration
, jenkinsConfig               : JenkinsConfig
, slackConfig                 : SlackConfig
, jenkinsConnector            : JenkinsConnector
, jenkinsJobsPersistence      : JenkinsJobsPersistence
, slackNotificationsConnector : SlackNotificationsConnector
) extends Logging:

  private val minDaysUnbuilt: Instant =
     Instant.now().minus(configuration.get[Int]("scheduler.rebuild.minDaysUnbuilt"), DAYS)

  def rebuildJobWithNoRecentBuild()(using ExecutionContext): Future[Unit] =
    (for
       job      <- EitherT.fromOptionF(
                     jenkinsJobsPersistence
                       .oldestServiceJob()
                       .map(_.filter(_.latestBuild.exists(_.timestamp.isBefore(minDaysUnbuilt))))
                   , logger.info("No old builds to trigger")
                   )
       _        <- EitherT.fromOptionF(
                     jenkinsConnector.getLatestBuildData(job.jenkinsUrl).map(_.filter(x => job.latestBuild.map(_.timestamp).contains(x.timestamp)))
                   , logger.info("Build already triggered")
                   )
       _        =  logger.info(s"Triggering job ${job.jobName} for repo: ${job.repoName}")
       queueUrl <- EitherT.right[Unit](jenkinsConnector.triggerBuildJob(job.jenkinsUrl))
       queue    <- EitherT.right[Unit](getQueue(queueUrl))
       queueExe <- EitherT.fromOption[Future](
                     queue.executable
                   , logger.warn(s"job ${job.jobName} for repo: ${job.repoName} - could not find queue executable")
                   )
       build    <- EitherT.right[Unit](getBuild(queueExe.url))
       _        <- build.result match
                     case Some(JenkinsConnector.LatestBuild.BuildResult.Failure) => EitherT.right[Unit](sendBuildFailureAlert(job.repoName, build))
                     case _                                                      => EitherT.left[Unit](Future.unit)
     yield ()
    ).merge

  private given ActorSystem = ActorSystem()

  private def getQueue(queueUrl: String)(using ExecutionContext): Future[JenkinsConnector.JenkinsQueueData] =
    Source.repeat(())
      .throttle(1, jenkinsConfig.queueThrottleDuration)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getQueueDetails(queueUrl))
      .filter(queue => queue.cancelled.nonEmpty || queue.executable.nonEmpty)
      .map: queue =>
        if queue.cancelled.nonEmpty && queue.cancelled.get then
          throw Exception("Queued Job cancelled")
        queue
      .filter(_.executable.nonEmpty)
      .runWith(Sink.head)

  private def getBuild(buildUrl: String)(using ExecutionContext): Future[JenkinsConnector.LatestBuild] =
    Source.repeat(())
      .throttle(1, jenkinsConfig.buildThrottleDuration)
      .mapAsync(parallelism = 1)(_ => jenkinsConnector.getBuild(buildUrl))
      .filter(_.result.nonEmpty)
      .runWith(Sink.head)

  import play.api.libs.json.Json
  private def sendBuildFailureAlert(serviceName: String, build: JenkinsConnector.LatestBuild)(using ExecutionContext): Future[Unit] =
    if slackConfig.enabled then
      logger.info(s"Rebuild failed for $serviceName")
      val message = slackConfig.messageText.replace("{serviceName}", serviceName)
      for
        rsp <- slackNotificationsConnector.sendMessage(SlackNotificationRequest(
                 channelLookup = ChannelLookup.RepositoryChannel(serviceName)
               , displayName   = "Automatic Rebuilder"
               , emoji         = ":hammer_and_wrench:"
               , text          = message
               , blocks        = jsSection(message)                          ::
                                 Json.parse("""{"type": "divider"}""")       ::
                                 jsSection(s"<${build.url}|$serviceName>") ::
                                 Nil
               ))
        if rsp.errors.nonEmpty
        _   =  logger.error(s"Errors sending rebuild FAILED notification: ${rsp.errors.mkString("[", ",", "]")}")
        if rsp.errors.nonEmpty
        err <- slackNotificationsConnector.sendMessage(SlackNotificationRequest(
                 channelLookup = ChannelLookup.SlackChannel(List(slackConfig.adminChannel))
               , displayName   = "Automatic Rebuilder"
               , emoji         = ":hammer_and_wrench:"
               , text          = s"Automatic Rebuilder failed to deliver slack message for service: $serviceName"
               , blocks        = jsSection(s"Failed to deliver the following slack message to intended channel(s).\\n$message") ::
                                 jsSection(rsp.errors.map(" - " + _.message).mkString("\\n"))                                   ::
                                 Json.parse("""{"type": "divider"}""")                                                          ::
                                 jsSection(s"<${build.url}|$serviceName>")                                                    ::
                                 Nil
               ))
        if err.errors.nonEmpty
        _          =  logger.error(s"Errors sending rebuild alert FAILED notification: ${err.errors.mkString("[", ",", "]")} - alert slackChannel = ${slackConfig.adminChannel}")
      yield ()
    else
      Future.unit

  private def jsSection(mrkdwn: String) =
    Json.parse(s"""{"type": "section", "text": {"type": "mrkdwn", "text": "$mrkdwn"}}""")
