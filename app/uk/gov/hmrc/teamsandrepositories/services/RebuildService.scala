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
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector
import uk.gov.hmrc.teamsandrepositories.models.BuildJob

import java.time.{Instant, ZonedDateTime}
import scala.annotation.tailrec
import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class RebuildService @Inject()(
  githubConfig    : GithubConfig,
  githubConnector : GithubConnector,
  timestamper     : Timestamper,
  configuration   : Configuration,
  jenkinsService: JenkinsService
) {
  private val logger = Logger(this.getClass)

  val sharedRepos: List[String] =
    configuration.get[Seq[String]]("shared.repositories").toList

  val dataSource: GithubV3RepositoryDataSource =
    new GithubV3RepositoryDataSource(
      githubConfig    = githubConfig,
      githubConnector = githubConnector,
      timestampF      = timestamper.timestampF,
      sharedRepos     = sharedRepos,
      configuration   = configuration
    )


  def getBuildJobDetails(serviceName: String): Future[Option[BuildJob]] = jenkinsService.findByService(serviceName)

  def getBuildJobs()(implicit ec: ExecutionContext): Future[Seq[RebuildJobData]] = {
    val thirtyDaysAgo = ZonedDateTime.now().minusDays(30).toInstant
    for {
      filenames <- dataSource.getBuildTeamFiles
      files <- Future.sequence(filenames.map { dataSource.getTeamFile})
      serviceNames <- Future.successful(
        files.filter(_.nonEmpty)
          .flatMap(extractServiceNames))
      buildJobs <- Future.sequence(serviceNames.map {getBuildJobDetails})
      jobsWithLatestBuildOver30days <- Future.successful(
        buildJobs.flatten
          .filter(_.latestBuild.nonEmpty)
          .map(job => RebuildJobData(job.service, job.jenkinsURL, job.latestBuild.get.timestamp))
          .filter(_.lastBuildTime.isBefore(thirtyDaysAgo))
          .sortBy(ele => ele.lastBuildTime))
    } yield jobsWithLatestBuildOver30days
  }

  private def extractServiceNames(x: String): Iterable[String] = {
    val statements = new AstBuilder().buildFromString(CompilePhase.CONVERSION, true, x)
      .toList
      .filter(n => n.isInstanceOf[BlockStatement]).head.asInstanceOf[BlockStatement]
      .getStatements
      .filter(p => p.isInstanceOf[ExpressionStatement])
      .map(p => p.asInstanceOf[ExpressionStatement].getExpression)
    val declarations = statements.filter(p => p.isInstanceOf[DeclarationExpression])
      .map(p => p.asInstanceOf[DeclarationExpression])
      .map(p => p.getVariableExpression.getName -> {
        p.getRightExpression match {
          case expression: ConstantExpression => expression.getValue.toString
          case expression: GStringExpression => expression.toString
          case expression => expression.getType.getName
        }
      })
      .toMap

    statements
      .map(p => getInitialCall(p))
      .filter(p => p.isInstanceOf[ConstructorCallExpression])
      .filter(p => p.getType.getName.equals("SbtMicroserviceJobBuilder"))
      .flatMap(p => p.asInstanceOf[ConstructorCallExpression]
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

  private def convertToRebuildJobData(builds: Seq[BuildJob]) = {
    builds.map(job => {
      RebuildJobData(job.service, job.jenkinsURL, job.latestBuild.get.timestamp)
    })
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