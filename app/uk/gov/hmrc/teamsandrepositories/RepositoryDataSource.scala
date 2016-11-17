/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime

import akka.actor.ActorSystem
import play.Logger
import play.api.libs.json._
import uk.gov.hmrc.githubclient.{GhOrganisation, GhRepository, GhTeam, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.RepoType._
import uk.gov.hmrc.teamsandrepositories.RetryStrategy._
import uk.gov.hmrc.teamsandrepositories.config.{CacheConfig, GithubConfigProvider}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.Source
import scala.util.{Failure, Success, Try}


case class TeamRepositories(teamName: String, repositories: List[Repository]) {
  def repositoriesByType(repoType: RepoType.RepoType) = repositories.filter(_.repoType == repoType)
}


case class Repository(name: String,
                      description: String,
                      url: String,
                      createdDate: Long,
                      lastActiveDate: Long,
                      isInternal: Boolean = false,
                      repoType: RepoType = RepoType.Other)

trait RepositoryDataSource {
  def getTeamRepoMapping: Future[Seq[TeamRepositories]]
}

class GithubV3RepositoryDataSource(val gh: GithubApiClient,
                                   val isInternal: Boolean) extends RepositoryDataSource {
  self: GithubConfigProvider =>

  import BlockingIOExecutionContext._

  implicit val repositoryFormats = Json.format[Repository]

  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  val retries: Int = 5
  val initialDuration: Double = 10

  def getTeamRepoMapping: Future[Seq[TeamRepositories]] =
    exponentialRetry(retries, initialDuration) {
      gh.getOrganisations.flatMap { orgs =>
        Future.sequence(orgs.map(mapOrganisation)).map {
          _.flatten
        }
      }
    }

  def mapOrganisation(organisation: GhOrganisation): Future[List[TeamRepositories]] =
    exponentialRetry(retries, initialDuration) {
      gh.getTeamsForOrganisation(organisation.login).flatMap { teams =>
        Future.sequence(for {
          team <- teams if !githubConfig.hiddenTeams.contains(team.name)
        } yield mapTeam(organisation, team))
      }
    }


  def mapTeam(organisation: GhOrganisation, team: GhTeam): Future[TeamRepositories] =
    exponentialRetry(retries, initialDuration) {
      gh.getReposForTeam(team.id).flatMap { repos =>
        Future.sequence(for {
          repo <- repos; if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
        } yield mapRepository(organisation, repo)).map { (repos: List[Repository]) =>
          TeamRepositories(team.name, repositories = repos)
        }
      }
    }


  private def mapRepository(organisation: GhOrganisation, repo: GhRepository): Future[Repository] = {

    isDeployable(repo, organisation) flatMap { deployable =>

      val repository: Repository = Repository(repo.name, repo.description, repo.htmlUrl, createdDate = repo.createdDate, lastActiveDate = repo.lastActiveDate, isInternal = this.isInternal)

      if (deployable)

        Future.successful(repository.copy(repoType = RepoType.Deployable))

      else isLibrary(repo, organisation).map { tags =>

        if (tags) repository.copy(repoType = RepoType.Library)

        else repository
      }

    }
  }

  private def isLibrary(repo: GhRepository, organisation: GhOrganisation) = {
    import uk.gov.hmrc.teamsandrepositories.FutureHelpers._

    def hasSrcMainScala =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "src/main/scala"))

    def hasSrcMainJava =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "src/main/java"))

    def containsTags =
      hasTags(organisation, repo)

    (hasSrcMainScala || hasSrcMainJava) && containsTags
  }

  private def isDeployable(repo: GhRepository, organisation: GhOrganisation) = {
    import uk.gov.hmrc.teamsandrepositories.FutureHelpers._

    def isPlayServiceF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "conf/application.conf"))

    def hasProcFileF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "Procfile"))

    def isJavaServiceF =
      exponentialRetry(retries, initialDuration)(hasPath(organisation, repo, "deploy.properties"))

    isPlayServiceF || isJavaServiceF || hasProcFileF
  }

  private def hasTags(organisation: GhOrganisation, repository: GhRepository) =
    gh.getTags(organisation.login, repository.name).map(_.nonEmpty)

  private def hasPath(organisation: GhOrganisation, repo: GhRepository, path: String) =
    gh.repoContainsContent(path, repo.name, organisation.login)
}

class CompositeRepositoryDataSource(val dataSources: List[RepositoryDataSource]) extends RepositoryDataSource {

  import BlockingIOExecutionContext._

  override def getTeamRepoMapping: Future[Seq[TeamRepositories]] =
    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      val flattened: List[TeamRepositories] = results.flatten

      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
      flattened.groupBy(_.teamName).map { case (name, teams) =>
        TeamRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
      }.toList
    }
}


class CachingRepositoryDataSource[T](akkaSystem: ActorSystem,
                                     cacheConfig: CacheConfig,
                                     dataSource: () => Future[T],
                                     timeStamp: () => LocalDateTime,
                                     enabled: Boolean = true) extends AbstractRepositoryDataSource[T] {

  private var cachedData: Option[CachedResult[T]] = None
  private val initialPromise = Promise[CachedResult[T]]()

  import ExecutionContext.Implicits._

  dataUpdate()

  private def fromSource = {
    dataSource().map { d => {
      val stamp = timeStamp()
      Logger.debug(s"Cache reloaded at $stamp")
      new CachedResult(d, stamp)
    }
    }
  }

  def getCachedTeamRepoMapping: Future[CachedResult[T]] = {
    Logger.info(s"cachedData is available = ${cachedData.isDefined}")
    if (cachedData.isEmpty && initialPromise.isCompleted) {
      Logger.warn("in unexpected state where initial promise is complete but there is not cached data. Perform manual reload.")
    }
    cachedData.fold(initialPromise.future)(d => Future.successful(d))
  }

  def reload() = {
    Logger.info(s"Manual teams repository cache reload triggered")
    dataUpdate()
  }

  Logger.info(s"Initialising cache reload every ${cacheConfig.teamsCacheDuration}")
  akkaSystem.scheduler.schedule(cacheConfig.teamsCacheDuration, cacheConfig.teamsCacheDuration) {
    Logger.info("Scheduled teams repository cache reload triggered")
    dataUpdate()
  }

  private def dataUpdate() {

    fromSource.onComplete {
      case Failure(e) => Logger.warn(s"failed to get latest data due to ${e.getMessage}", e)
      case Success(d) => {
        synchronized {
          this.cachedData = Some(d)
          Logger.info(s"data update completed successfully")

          if (!initialPromise.isCompleted) {
            Logger.debug("early clients being sent result")
            this.initialPromise.success(d)
          }
        }
      }
    }
  }
}

abstract class AbstractRepositoryDataSource[T] {
  def getCachedTeamRepoMapping: Future[CachedResult[T]]

  def reload(): Unit
}


class FileRepositoryDataSource(cacheFilename: String) extends AbstractRepositoryDataSource[Seq[TeamRepositories]] {

  implicit val repositoryFormats = Json.format[Repository]
  implicit val teamRepositoryFormats = Json.format[TeamRepositories]

  private var cachedData = loadCacheData

  def loadCacheData: Option[CachedResult[Seq[TeamRepositories]]] = {
    Try(Json.parse(Source.fromFile(cacheFilename).mkString)
      .as[Seq[TeamRepositories]]) match {
      case Success(repos) => Some(new CachedResult(repos, LocalDateTime.now))
      case Failure(e) =>
        e.printStackTrace()
        None
    }
  }

  override def getCachedTeamRepoMapping: Future[CachedResult[Seq[TeamRepositories]]] = {
    Future.successful(cachedData.get)
  }

  override def reload(): Unit = cachedData = loadCacheData
}