/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import reactivemongo.api.commands.UpdateWriteResult
import uk.gov.hmrc.teamsandrepositories.connectors.JenkinsConnector
import uk.gov.hmrc.teamsandrepositories.persitence.BuildJobRepo
import uk.gov.hmrc.teamsandrepositories.persitence.model.BuildJob

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class JenkinsService @Inject()(repo: BuildJobRepo, jenkinsConnector: JenkinsConnector){

  def findByService(service: String): Future[Option[BuildJob]] = {
    repo.findByService(service)
  }

  def updateBuildJobs(): Future[Seq[UpdateWriteResult]] = {
    for {
      res <- jenkinsConnector.findBuildJobRoot()
      buildJobs = res.map(build => BuildJob(build.displayName, build.url))
      persist <- repo.update(buildJobs)
    } yield persist
  }
}