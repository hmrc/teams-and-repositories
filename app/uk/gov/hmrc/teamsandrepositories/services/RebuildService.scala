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

package uk.gov.hmrc.teamsandrepositories.services

import com.google.inject.{Inject, Singleton}
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.{ArgumentListExpression, ConstantExpression, ConstructorCallExpression, DeclarationExpression, Expression, GStringExpression, MethodCallExpression, VariableExpression}
import org.codehaus.groovy.ast.stmt.{BlockStatement, ExpressionStatement}
import org.codehaus.groovy.control.CompilePhase
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{Json, OWrites, __}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.config.SlackConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{BuildJobsConnector, ChannelLookup, SlackNotificationRequest, SlackNotificationsConnector}
import uk.gov.hmrc.teamsandrepositories.models.{BuildData, BuildResult}

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
case class RebuildService @Inject()(
  configuration               : Configuration,
  slackConfig                 : SlackConfig,
  buildJobsConnector          : BuildJobsConnector,
  jenkinsService              : JenkinsService,
  slackNotificationsConnector : SlackNotificationsConnector
) {
  private val logger = Logger(this.getClass)

  private val minDaysUnbuilt: Int = configuration.get[Int]("scheduler.rebuild.minDaysUnbuilt")

  def rebuildJobWithNoRecentBuild()(implicit ec: ExecutionContext): Future[Unit] =
    getJobsWithNoBuildFor(minDaysUnbuilt).map(jobs =>
      if (jobs.isEmpty) {
        logger.info("No old builds to trigger")
        ()
      } else {
        val oldestJob = jobs.head
        for {
          build <- jenkinsService.triggerBuildJob(oldestJob.name, oldestJob.jenkinsURL, oldestJob.lastBuildTime)

          if build.nonEmpty && build.get.result.nonEmpty && build.get.result.get == BuildResult.Failure
          _     <- sendBuildFailureAlert(build.get, oldestJob.name)
        } yield ()
      }
    )

  private def sendBuildFailureAlert(build: BuildData, serviceName: String)(implicit ec: ExecutionContext) =
    if (slackConfig.enabled) {
      logger.info(s"Rebuild failed for $serviceName")
      val message = slackConfig.messageText.replace("{serviceName}", serviceName)
      for {
        rsp        <- slackNotificationsConnector.sendMessage(SlackNotificationRequest(
                        channelLookup = ChannelLookup.RepositoryChannel(serviceName)
                      , displayName   = "Automatic Rebuilder"
                      , emoji         = ":hammer_and_wrench:"
                      , text          = message
                      , blocks        = jsSection(message)                        ::
                                        Json.parse("""{"type": "divider"}""")     ::
                                        jsSection(s"<${build.url}|$serviceName>") ::
                                        Nil
                      ))
        if !rsp.hasSentMessages
        _          =  logger.error(s"Errors sending rebuild FAILED notification: ${rsp.errors.mkString("[", ",", "]")}")

        if rsp.errors.nonEmpty
        errRsp     <- slackNotificationsConnector.sendMessage(SlackNotificationRequest(
                        channelLookup = ChannelLookup.SlackChannel(List(slackConfig.adminChannel))
                      , displayName   = "Automatic Rebuilder"
                      , emoji         = ":hammer_and_wrench:"
                      , text          = s"Automatic Rebuilder failed to deliver slack message for service: $serviceName"
                      , blocks        = jsSection(s"Failed to deliver the following slack message to intended channel(s).\\n$message") ::
                                        jsSection(rsp.errors.map(" - " + _.message).mkString("\\n"))                                               ::
                                        Json.parse("""{"type": "divider"}""")                                                                      ::
                                        jsSection(s"<${build.url}|$serviceName>")                                                                  ::
                                        Nil
                      ))
        if !errRsp.hasSentMessages
        _          =  logger.error(s"Errors sending rebuild alert FAILED notification: ${errRsp.errors.mkString("[", ",", "]")} - alert slackChannel = ${slackConfig.adminChannel}")
      } yield rsp
    }
    else
      Future.unit

  private def jsSection(mrkdwn: String) = Json.parse(s"""{"type": "section", "text": {"type": "mrkdwn", "text": "$mrkdwn"}}""")

  private def getJobsWithNoBuildFor(daysUnbuilt: Int)(implicit ec: ExecutionContext): Future[Seq[RebuildJobData]] = {
    val cutoff = Instant.now().minus(daysUnbuilt, DAYS)
    for {
      filenames                     <- buildJobsConnector.getBuildjobFiles()
      files                         <- Future.traverse(filenames)(buildJobsConnector.getBuildjobFileContent)
      serviceNames                  =  files.filter(_.nonEmpty).flatMap(extractServiceNames)
      buildJobs                     <- Future.traverse(serviceNames)(jenkinsService.findByJobName)
      jobsWithLatestBuildOver30days =  buildJobs.flatten
                                         .filter(_.latestBuild.nonEmpty)
                                         .map(job => RebuildJobData(job.jobName, job.jenkinsUrl, job.latestBuild.get.timestamp))
                                         .filter(_.lastBuildTime.isBefore(cutoff))
                                         .sortBy(ele => ele.lastBuildTime)
    } yield jobsWithLatestBuildOver30days
  }

  private def extractServiceNames(buildFileContents: String): Iterable[String] = {
    val statements =
      new AstBuilder().buildFromString(CompilePhase.CONVERSION, true, buildFileContents)
        .asScala
        .toList
        .collect { case x: BlockStatement => x }
        .head
        .getStatements
        .asScala
        .collect { case expressionStatement: ExpressionStatement => expressionStatement }
        .map(_.getExpression)

    val declarations =
      statements
        .collect { case declarationExpression: DeclarationExpression => declarationExpression }
        .map { declarationExpression =>
          declarationExpression.getVariableExpression.getName ->
            (declarationExpression.getRightExpression match {
              case expression: ConstantExpression => expression.getValue.toString
              case expression: GStringExpression  => expression.toString
              case expression => expression.getType.getName
            })
        }
        .toMap

    statements
      .map(getInitialCall)
      .collect { case constructorCall: ConstructorCallExpression => constructorCall }
      .filter(_.getType.getName.equals("SbtMicroserviceJobBuilder"))
      .flatMap(_.getArguments.asInstanceOf[ArgumentListExpression].getExpression(1) match {
        case expression: ConstantExpression => Some(expression.getValue.toString)
        case expression: VariableExpression => declarations.get(expression.getName)
        case _ => None
      })
  }

  @tailrec
  private def getInitialCall(expression: Expression): Expression =
    expression match {
      case expression: MethodCallExpression => getInitialCall(expression.getObjectExpression)
      case expression => expression
    }

  case class RebuildJobData(
    name         : String,
    jenkinsURL   : String,
    lastBuildTime: Instant
  )

  object RebuildJobData {
    implicit val apiWrites: OWrites[RebuildJobData] =
      ( (__ \ "name"         ).write[String]
      ~ (__ \ "jenkinsURL"   ).write[String]
      ~ (__ \ "lastBuildTime").write[Instant]
      )(unlift(unapply))
  }
}
