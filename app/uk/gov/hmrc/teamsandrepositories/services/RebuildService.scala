/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.{OWrites, __}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.config.SlackConfig
import uk.gov.hmrc.teamsandrepositories.connectors.{ChannelLookup, JenkinsBuildData, MessageDetails, SlackNotificationRequest, SlackNotificationsConnector}
import uk.gov.hmrc.teamsandrepositories.models.BuildResult.Failure

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import scala.annotation.tailrec
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class RebuildService @Inject()(
  dataSource                  : GithubV3RepositoryDataSource,
  configuration               : Configuration,
  slackConfig                 : SlackConfig,
  jenkinsService              : JenkinsService,
  slackNotificationsConnector : SlackNotificationsConnector
) {
  private val logger = Logger(this.getClass)

  private val minDaysUnbuilt: Int = configuration.get[Int]("scheduler.rebuild.minDaysUnbuilt")

  def rebuildJobWithNoRecentBuild()(implicit ec: ExecutionContext): Future[Unit] = {
    val oldBuiltJobs = getJobsWithNoBuildFor(minDaysUnbuilt)
    oldBuiltJobs.map(jobs => {
      if (jobs.isEmpty) {
        logger.info("No old builds to trigger")
        Unit
      } else {
        val oldestJob = jobs.head
        for {
          build <- jenkinsService.triggerBuildJob(oldestJob.service, oldestJob.jenkinsURL, oldestJob.lastBuildTime)
          _ <- sendBuildFailureAlert(build, oldestJob.service) if build.result.nonEmpty && build.result.get == Failure
        } yield ()
      }
    })
  }
  private def sendBuildFailureAlert(build: JenkinsBuildData, serviceName: String)(implicit ec: ExecutionContext) = {
      val channelLookup: ChannelLookup = ChannelLookup(serviceName)
      val messageDetails: MessageDetails = MessageDetails(
        slackConfig.messageText.replace("serviceName", serviceName).replace("buildUrl", build.url),
        slackConfig.user,
        "",
        Seq())
    for {
      response <- slackNotificationsConnector.sendMessage(SlackNotificationRequest(channelLookup, messageDetails)) if slackConfig.enabled
      _ = logger.error(s"Errors sending rebuild FAILED notification: ${response.errors.mkString("[", ",", "]")}") if !response.hasSentMessages
    } yield response
  }

  def getJobsWithNoBuildFor(daysUnbuilt: Int)(implicit ec: ExecutionContext): Future[Seq[RebuildJobData]] = {
    val thirtyDaysAgo = Instant.now().minus(daysUnbuilt, DAYS)
    for {
      filenames <- dataSource.getBuildTeamFiles
      files <- Future.traverse(filenames) { dataSource.getTeamFile }
      serviceNames = files.filter(_.nonEmpty).flatMap(extractServiceNames)
      buildJobs <- Future.traverse(serviceNames) { jenkinsService.findByService }
      jobsWithLatestBuildOver30days =
        buildJobs.flatten
          .filter(_.latestBuild.nonEmpty)
          .map(job => RebuildJobData(job.service, job.jenkinsURL, job.latestBuild.get.timestamp))
          .filter(_.lastBuildTime.isBefore(thirtyDaysAgo))
          .sortBy(ele => ele.lastBuildTime)
    } yield jobsWithLatestBuildOver30days
  }

  private def extractServiceNames(buildFileContents: String): Iterable[String] = {
    val statements = new AstBuilder().buildFromString(CompilePhase.CONVERSION, true, buildFileContents)
      .toList
      .collect { case x: BlockStatement => x }
      .head
      .getStatements
      .collect { case expressionStatement: ExpressionStatement => expressionStatement }
      .map(_.getExpression)
    val declarations = statements
      .collect { case declarationExpression: DeclarationExpression => declarationExpression }
      .map(declarationExpression => declarationExpression.getVariableExpression.getName -> {
        declarationExpression.getRightExpression match {
          case expression: ConstantExpression => expression.getValue.toString
          case expression: GStringExpression => expression.toString
          case expression => expression.getType.getName
        }
      })
      .toMap

    statements
      .map(getInitialCall)
      .collect { case constructorCall: ConstructorCallExpression => constructorCall }
      .filter(_.getType.getName.equals("SbtMicroserviceJobBuilder"))
      .flatMap(_.getArguments.asInstanceOf[ArgumentListExpression]
        .getExpression(1) match {
        case expression: ConstantExpression => Some(expression.getValue.toString)
        case expression: VariableExpression => declarations.get(expression.getName)
        case _ => None
      })
  }

  @tailrec
  private def getInitialCall(expression: Expression): Expression = {
    expression match {
      case expression: MethodCallExpression => getInitialCall(expression.getObjectExpression)
      case expression => expression
    }
  }

  case class RebuildJobData(
                             service: String,
                             jenkinsURL: String,
                             lastBuildTime: Instant)

  object RebuildJobData {
    implicit val apiWrites: OWrites[RebuildJobData] =
      ((__ \ "service").write[String]
      ~ (__ \ "jenkinsURL").write[String]
      ~ (__ \ "lastBuildTime").write[Instant]
      ) (unlift(unapply))
  }
}