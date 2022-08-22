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

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import scala.annotation.tailrec
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class RebuildService @Inject()(
  dataSource      : GithubV3RepositoryDataSource,
  configuration   : Configuration,
  jenkinsService  : JenkinsService
) {
  private val logger = Logger(this.getClass)

  private val minDaysUnbuilt: Int = configuration.get[Int]("scheduler.rebuild.minDaysUnbuilt")

  val sharedRepos: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  def rebuildJobWithNoRecentBuild()(implicit ec: ExecutionContext): Future[Unit] = {
    val oldBuiltJobs = getJobsWithNoBuildFor(minDaysUnbuilt)
    oldBuiltJobs.map(jobs => {
      if (jobs.isEmpty) {
        logger.info("No old builds to trigger")
        Unit
      } else {
        val oldestJob = jobs.head
        jenkinsService.triggerBuildJob(oldestJob.service, oldestJob.jenkinsURL, oldestJob.lastBuildTime)
      }
    })
  }

  def getJobsWithNoBuildFor(daysUnbuilt: Int)(implicit ec: ExecutionContext): Future[Seq[RebuildJobData]] = {
    val thirtyDaysAgo = Instant.now().minus(daysUnbuilt, DAYS)
    for {
      filenames <- dataSource.getBuildTeamFiles
      files <- Future.traverse(filenames) { dataSource.getTeamFile }
      serviceNames <- Future.successful(
        files.filter(_.nonEmpty)
          .flatMap(extractServiceNames))
      buildJobs <- Future.sequence(serviceNames.map {jenkinsService.findByService})
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
      .filter(_.isInstanceOf[BlockStatement]).head.asInstanceOf[BlockStatement]
      .getStatements
      .filter(_.isInstanceOf[ExpressionStatement])
      .map(_.asInstanceOf[ExpressionStatement].getExpression)
    val declarations = statements.filter(_.isInstanceOf[DeclarationExpression])
      .map(_.asInstanceOf[DeclarationExpression])
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
      .filter(_.isInstanceOf[ConstructorCallExpression])
      .filter(_.getType.getName.equals("SbtMicroserviceJobBuilder"))
      .flatMap(_.asInstanceOf[ConstructorCallExpression]
        .getArguments.asInstanceOf[ArgumentListExpression]
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