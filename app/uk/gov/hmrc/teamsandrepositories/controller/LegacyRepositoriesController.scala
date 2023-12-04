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

package uk.gov.hmrc.teamsandrepositories.controller

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json.toJson
import play.api.libs.json.Format
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.teamsandrepositories.models._
import uk.gov.hmrc.teamsandrepositories.config.UrlTemplatesProvider
import uk.gov.hmrc.teamsandrepositories.controller.model.RepositoryDetails
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import scala.concurrent.ExecutionContext

/*
 Continues to exist to provide api compatibility to services that have yet to migrate to the V2 api.
 */
@Singleton
class LegacyRepositoriesController @Inject()(
  repositoriesPersistence : RepositoriesPersistence,
  urlTemplatesProvider    : UrlTemplatesProvider,
  cc                      : ControllerComponents
)(implicit
  ec                      : ExecutionContext
) extends BackendController(cc) {

  private implicit val rdf: Format[RepositoryDetails] = RepositoryDetails.format
  private implicit val dsf: Format[DigitalService]    = DigitalService.format

  def repositoryTeams = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived = None).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.getRepositoryToTeamNames(allTeamsAndRepos)))
    }
  }

  def repositories(archived: Option[Boolean]) = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived).map { allTeamsAndRepos =>
      Ok(toJson(TeamRepositories.getAllRepositories(allTeamsAndRepos)))
    }
  }

  def repositoryDetails(name: String) = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        TeamRepositories.findRepositoryDetails(allTeamsAndRepos, name, urlTemplatesProvider.ciUrlTemplates) match {
          case None                    => NotFound
          case Some(repositoryDetails) => Ok(toJson(repositoryDetails))
        }
      }
  }

  def services(details: Boolean) = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        val json =
          if (details)
            toJson(TeamRepositories.getRepositoryDetailsList(allTeamsAndRepos, RepoType.Service, urlTemplatesProvider.ciUrlTemplates))
          else
            toJson(TeamRepositories.getAllRepositories(allTeamsAndRepos).filter(_.repoType == RepoType.Service))
        Ok(json)
      }
  }

  def libraries(details: Boolean) = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        val json =
          if (details)
            toJson(TeamRepositories.getRepositoryDetailsList(allTeamsAndRepos, RepoType.Library, urlTemplatesProvider.ciUrlTemplates))
          else
            toJson(TeamRepositories.getAllRepositories(allTeamsAndRepos).filter(_.repoType == RepoType.Library))
        Ok(json)
      }
  }

  def digitalServices = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        val digitalServices: Seq[String] =
          allTeamsAndRepos
            .flatMap(_.repositories)
            .flatMap(_.digitalServiceName)
            .distinct
            .sorted

        Ok(toJson(digitalServices))
      }
  }

  def digitalServiceDetails(name: String) = Action.async {
    repositoriesPersistence.getAllTeamsAndRepos(archived = None)
      .map { allTeamsAndRepos =>
        TeamRepositories.findDigitalServiceDetails(allTeamsAndRepos, name) match {
          case None                 => NotFound
          case Some(digitalService) => Ok(toJson(digitalService))
        }
      }
  }

}
