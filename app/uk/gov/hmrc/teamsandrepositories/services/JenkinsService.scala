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
