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
import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OWrites, __}
import play.api.{Configuration, Logger}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GithubConnector
import uk.gov.hmrc.teamsandrepositories.models.BuildJob

import java.time.{Instant, ZonedDateTime}
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
      serviceNames <- Future.successful(files.flatMap(extractServiceNames))
      buildJobs <- Future.sequence(serviceNames.map { getBuildJobDetails })
      jobsWithBuilds <- Future.successful(buildJobs
        .flatten
        .filter(_.builds.nonEmpty))
      oldBuilds <- Future.successful(convertToRebuildJobData(jobsWithBuilds)
        .filter(_.lastBuildTime.isBefore(thirtyDaysAgo)))
    } yield oldBuilds
  }

  //NB this method will break on some repos and needs to be replaced using a Groovy parser
  private def extractServiceNames(x: String): Array[String] = x
    .split("\n")
    .filter(_.contains("new SbtMicroserviceJobBuilder"))
    .map(x => x.split(",")(1).replace("'", "").trim)

  private def convertToRebuildJobData(builds: Seq[BuildJob]) = {
    builds.map(job => {
      val latestJob = job.builds.maxBy(_.timestamp)
      RebuildJobData(job.service, job.jenkinsURL, latestJob.timestamp)
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