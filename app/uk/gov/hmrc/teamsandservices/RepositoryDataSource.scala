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

package uk.gov.hmrc.teamsandservices

import java.time.LocalDateTime
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import play.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.githubclient.{GhOrganisation, GhRepository, GhTeam, GithubApiClient}
import uk.gov.hmrc.teamsandservices.RetryStrategy._
import uk.gov.hmrc.teamsandservices.config.{CacheConfig, GithubConfigProvider}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}


case class TeamRepositories(teamName: String, repositories: List[Repository])
case class Repository(name: String, url: String, isInternal: Boolean = false, deployable: Boolean = false)

trait RepositoryDataSource {
  def getTeamRepoMapping: Future[Seq[TeamRepositories]]
  def executionContext:ExecutionContext
}

class GithubV3RepositoryDataSource(val gh: GithubApiClient,
                                   val isInternal: Boolean,
                                   override implicit val executionContext:ExecutionContext) extends RepositoryDataSource {
  self: GithubConfigProvider =>

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
          team <- teams; if !githubConfig.hiddenTeams.contains(team.name)
        } yield mapTeam(organisation, team))
      }
    }


  def mapTeam(organisation: GhOrganisation, team: GhTeam) =
    exponentialRetry(retries, initialDuration) {
      gh.getReposForTeam(team.id).flatMap { repos =>
        Future.sequence(for {
          repo <- repos; if !repo.fork && !githubConfig.hiddenRepositories.contains(repo.name)
        } yield mapRepository(organisation, repo)).map { repos =>
          TeamRepositories(team.name, repositories = repos)
        }
      }
    }

  private def mapRepository(organisation: GhOrganisation, repo: GhRepository) =
    for {
      hasAppFolder <- exponentialRetry(retries, initialDuration) {
        hasPath(organisation, repo, "app")
      }
      hasProcfile <- exponentialRetry(retries, initialDuration) {
        hasPath(organisation, repo, "Procfile")
      }
    } yield {
      Repository(repo.name, repo.htmlUrl, isInternal = this.isInternal, deployable = hasAppFolder || hasProcfile)
    }

  def hasPath(organisation: GhOrganisation, repo: GhRepository, path: String): Future[Boolean] = {
    gh.repoContainsContent(path, repo.name, organisation.login)
  }
}

class CompositeRepositoryDataSource(val dataSources: List[RepositoryDataSource], implicit override val executionContext:ExecutionContext) extends RepositoryDataSource {
  override def getTeamRepoMapping: Future[Seq[TeamRepositories]] =
    Future.sequence(dataSources.map(_.getTeamRepoMapping)).map { results =>
      val flattened = results.flatten

      Logger.info(s"Combining ${flattened.length} results from ${dataSources.length} sources")
      flattened.groupBy(_.teamName).map { case (name, teams) =>
        TeamRepositories(name, teams.flatMap(t => t.repositories).sortBy(_.name))
      }.toList
    }
}


class CachingRepositoryDataSource[T](
                                      akkaSystem:ActorSystem,
                                      cacheConfig:CacheConfig,
                                      dataSource: () => Future[T],
                                      timeStamp: () => LocalDateTime) {

  private var cachedData: Option[CachedResult[T]] = None
  private val initialPromise = Promise[CachedResult[T]]()

  import ExecutionContext.Implicits._

  dataUpdate()

  private def fromSource = {
    dataSource().map { d => {
      val stamp = timeStamp()
      Logger.debug(s"Cache reloaded at $stamp")
      new CachedResult(d, stamp)
    }}
  }

  def getCachedTeamRepoMapping: Future[CachedResult[T]] = {
    Logger.info(s"cachedData is available = ${cachedData.isDefined}")
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

  private def dataUpdate(){
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
