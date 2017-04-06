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

import java.net.URLDecoder
import java.time.{LocalDateTime, ZoneOffset}

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.teamsandrepositories.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.teamsandrepositories.RepoType.RepoType
import uk.gov.hmrc.teamsandrepositories.TeamRepositoryWrapper.{RepositoriesToTeam, RepositoryToTeam, extractRepositoryGroupForType, repoGroupToRepositoryDetails}
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplates

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


case class TeamRepositories(teamName: String,
                            repositories: List[GitRepository]) {
  def repositoriesByType(repoType: RepoType.RepoType) = repositories.filter(_.repoType == repoType)
}

object TeamRepositories {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats = Json.format[TeamRepositories]

  def getTeamList(teamRepos: Seq[TeamRepositories], repositoriesToIgnore: List[String]): Seq[Team] =
    teamRepos.map(_.teamName).map { tn =>
      val repos: Seq[GitRepository] = teamRepos.filter(_.teamName == tn).flatMap(_.repositories)
      val team = Team(name = tn, repos = None)
      if (repos.nonEmpty) {
        val teamActivityDates = GitRepository.getTeamActivityDatesOfNonSharedRepos(repos, repositoriesToIgnore)
        team.copy(firstActiveDate = teamActivityDates.firstActiveDate, lastActiveDate = teamActivityDates.lastActiveDate)
      } else team

    }

  def getAllRepositories(teamRepos: Seq[TeamRepositories]): Seq[Repository] =
    teamRepos
      .flatMap(_.repositories)
      .groupBy(_.name)
      .map {
        case (repositoryName, repositories) =>
          Repository(
            repositoryName,
            repositories.minBy(_.createdDate).createdDate,
            repositories.maxBy(_.lastActiveDate).lastActiveDate,
            GitRepository.primaryRepoType(repositories))
      }
      .toList
      .sortBy(_.name.toUpperCase)

  def findRepositoryDetails(teamRepos: Seq[TeamRepositories], repoName: String, ciUrlTemplates: UrlTemplates): Option[RepositoryDetails] = {
    teamRepos.foldLeft((Set.empty[String], Set.empty[GitRepository])) { case ((ts, repos), tr) =>
      if (tr.repositories.exists(_.name == repoName))
        (ts + tr.teamName, repos ++ tr.repositories.filter(_.name == repoName))
      else (ts, repos)
    } match {
      case (teams, repos) if repos.nonEmpty =>
        repoGroupToRepositoryDetails(GitRepository.primaryRepoType(repos.toSeq), repos.toSeq, teams.toSeq.sorted, ciUrlTemplates)
      case _ => None
    }
  }

  def getTeamRepositoryNameList(teamRepos: Seq[TeamRepositories], teamName: String): Option[Map[RepoType.RepoType, List[String]]] = {
    val decodedTeamName = URLDecoder.decode(teamName, "UTF-8")
    teamRepos.find(_.teamName == decodedTeamName).map { t =>

      RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, rtype) =>
        m + (rtype -> extractRepositoryGroupForType(rtype, t.repositories).map(_.name).distinct.sortBy(_.toUpperCase))
      }

    }
  }

  def getRepositoryDetailsList(teamRepos: Seq[TeamRepositories], repoType: RepoType, ciUrlTemplates: UrlTemplates): Seq[RepositoryDetails] = {
    getRepositoryTeams(teamRepos)
      .groupBy(_.repositories)
      .flatMap { case (repositories, teamsAndRepos: Seq[RepositoriesToTeam]) => repoGroupToRepositoryDetails(repoType, repositories, teamsAndRepos.map(_.teamName), ciUrlTemplates) }
      .toSeq
      .sortBy(_.name.toUpperCase)
  }

  def getRepositoryTeams(data: Seq[TeamRepositories]): Seq[RepositoriesToTeam] =
    for {
      teamAndRepositories <- data
      repositories <- teamAndRepositories.repositories.groupBy(_.name).values
    } yield RepositoriesToTeam(repositories, teamAndRepositories.teamName)

  def findTeam(teamRepos: Seq[TeamRepositories], teamName: String, repositoriesToIgnore: List[String]): Option[Team] = {

    teamRepos
      .find(_.teamName == URLDecoder.decode(teamName, "UTF-8"))
      .map { teamRepositories =>

        val teamActivityDates = GitRepository.getTeamActivityDatesOfNonSharedRepos(teamRepositories.repositories, repositoriesToIgnore)

        def getRepositoryDisplayDetails(repoType: RepoType.Value): List[String] = {
          teamRepositories.repositories
            .filter(_.repoType == repoType)
            .map(_.name)
            .distinct
            .sortBy(_.toUpperCase)
        }

        val repos = RepoType.values.foldLeft(Map.empty[RepoType.Value, List[String]]) { case (m, repoType) =>
          m + (repoType -> getRepositoryDisplayDetails(repoType))
        }

        Team(teamName, teamActivityDates.firstActiveDate, teamActivityDates.lastActiveDate, teamActivityDates.firstServiceCreationDate, Some(repos))
      }
  }

  def getRepositoryToTeamNameList(teamRepos: Seq[TeamRepositories]): Map[String, Seq[String]] = {
    val mappings = for {
      tr <- teamRepos
      r <- tr.repositories
    } yield RepositoryToTeam(r.name, tr.teamName)

    mappings.groupBy(_.repositoryName)
      .map { m => m._1 -> m._2.map(_.teamName).distinct }
  }
}

class TeamsAndReposPersister @Inject()(mongoTeamsAndReposPersister: MongoTeamsAndRepositoriesPersister, mongoUpdateTimePersister: MongoUpdateTimePersister) {

  val teamsAndRepositoriesTimestampKeyName = "teamsAndRepositories.updated"


  def update(teamsAndRepositories: TeamRepositories): Future[TeamRepositories] = {
    mongoTeamsAndReposPersister.update(teamsAndRepositories)
  }

  def getAllTeamAndRepos: Future[(Seq[TeamRepositories], Option[LocalDateTime])] = {
    for {
      teamsAndRepos <- mongoTeamsAndReposPersister.getAllTeamAndRepos
      timestamp <- mongoUpdateTimePersister.get(teamsAndRepositoriesTimestampKeyName)
    } yield (teamsAndRepos, timestamp.map(_.timestamp))
  }

  def clearAllData: Future[Boolean] = {
    mongoTeamsAndReposPersister.clearAllData
    mongoUpdateTimePersister.remove(teamsAndRepositoriesTimestampKeyName)
  }

  def updateTimestamp(timestamp: LocalDateTime): Future[Boolean] = {
    mongoUpdateTimePersister.update(KeyAndTimestamp(teamsAndRepositoriesTimestampKeyName, timestamp))
  }

  def deleteTeams(teamNames: Set[String]): Future[Set[String]] = {
    Logger.info(s"Deleting orphan teams: $teamNames")
    Future.sequence(teamNames.map(mongoTeamsAndReposPersister.deleteTeam))
  }
}

@Singleton
class MongoTeamsAndRepositoriesPersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[TeamRepositories, BSONObjectID](
    collectionName = "teamsAndRepositories",
    mongo = mongoConnector.db,
    domainFormat = TeamRepositories.formats) {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("teamName" -> IndexType.Hashed), name = Some("teamNameIdx")))
      )
    )


  def update(teamAndRepos: TeamRepositories): Future[TeamRepositories] = {

    withTimerAndCounter("mongo.update") {
      for {
        update <- collection.update(selector = Json.obj("teamName" -> Json.toJson(teamAndRepos.teamName)), update = teamAndRepos, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw new RuntimeException(s"failed to persist $teamAndRepos")
        case _ => teamAndRepos
      }
    }
  }

  def add(teamsAndRepository: TeamRepositories): Future[Boolean] = {
    withTimerAndCounter("mongo.write") {
      insert(teamsAndRepository) map {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }

  def getAllTeamAndRepos: Future[List[TeamRepositories]] = findAll()

  def clearAllData: Future[Boolean] = super.removeAll().map(!_.hasErrors)

  def deleteTeam(teamName: String): Future[String] = {
    withTimerAndCounter("mongo.cleanup") {
      collection.remove(query = Json.obj("teamName" -> Json.toJson(teamName))).map {
        case lastError if lastError.inError => throw new RuntimeException(s"failed to remove $teamName")
        case _ => teamName
      }
    }
  }
}

case class KeyAndTimestamp(keyName: String, timestamp: LocalDateTime)

object KeyAndTimestamp {
  implicit val localDateTimeRead: Reads[LocalDateTime] =
    __.read[Long].map { dateTime => LocalDateTime.ofEpochSecond(dateTime, 0, ZoneOffset.UTC) }

  implicit val localDateTimeWrite: Writes[LocalDateTime] = new Writes[LocalDateTime] {
    def writes(dateTime: LocalDateTime): JsValue = JsNumber(value = dateTime.atOffset(ZoneOffset.UTC).toEpochSecond)
  }

  implicit val formats = Json.format[KeyAndTimestamp]
}


@Singleton
class MongoUpdateTimePersister @Inject()(mongoConnector: MongoConnector)
  extends ReactiveRepository[KeyAndTimestamp, BSONObjectID](
    collectionName = "updateTime",
    mongo = mongoConnector.db,
    domainFormat = KeyAndTimestamp.formats) {

  private val keyFieldName = "keyName"

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq(keyFieldName -> IndexType.Hashed), name = Some(keyFieldName + "Idx")))
      )
    )

  def get(keyName: String): Future[Option[KeyAndTimestamp]] = {
    withTimerAndCounter("mongo.timestamp.get") {
      collection.find(Json.obj(keyFieldName -> Json.toJson(keyName)))
        .cursor[KeyAndTimestamp]()
        .collect[List]().map(_.headOption)
    }
  }

  def update(keyAndTimestamp: KeyAndTimestamp): Future[Boolean] = {
    withTimerAndCounter("mongo.timestamp.update") {
      for {
        update <- collection.update(selector = Json.obj(keyFieldName -> Json.toJson(keyAndTimestamp.keyName)), update = keyAndTimestamp, upsert = true)
      } yield update match {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }

  def remove(keyName: String): Future[Boolean] = super.remove(keyFieldName -> Json.toJson(keyName)).map(!_.hasErrors)
}


