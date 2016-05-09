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

package uk.gov.hmrc.teamsandservices.teams

import play.api.libs.json.Json
import uk.gov.hmrc.teamsandservices.config.{UrlTemplate, UrlTemplates}

object ViewModels {

  case class TeamRepositories(teamName: String, repositories: List[Repository])

  case class TeamServices(teamName: String, Services: List[Service])

  case class Repository(name: String, url: String, isInternal: Boolean = false, isMicroservice: Boolean = false)

  case class Link(name: String, url: String)

  case class Environment(name: String, subEnvironments: Set[SubEnvironment])

  case class SubEnvironment(name: String)

  case class Service(name: String, githubUrl: Link, ci: List[Link])

  object Link {
    implicit val formats = Json.format[Link]
  }

  object Service {
    implicit val formats = Json.format[Service]

    def fromRepository(repository: Repository, urlTemplates: UrlTemplates): Option[Service] = {
      if (!repository.isMicroservice) None
      else Some(
        Service(
          repository.name,
          Link("github", repository.url),
          buildCiUrls(repository, urlTemplates)
        )
      )
    }

    private def buildCiUrls(repository: Repository, urlTemplates: UrlTemplates): List[Link] = {

      def buildUrls(templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.url(repository.name))).toList

      repository.isInternal match {
        case true => buildUrls(urlTemplates.ciClosed)
        case false => buildUrls(urlTemplates.ciOpen)
      }

    }
  }

  object Repository {
    implicit val formats = Json.format[Repository]
  }

  object TeamServices {
    implicit val formats = Json.format[TeamServices]
  }

  object TeamRepositories {
    implicit val formats = Json.format[TeamRepositories]
  }

}