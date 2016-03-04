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

package uk.gov.hmrc.catalogue.teams

import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.catalogue.config.{UrlTemplate, UrlTemplates}

object ViewModels {

  case class TeamRepositories(teamName: String, repositories: List[Repository])

  case class TeamServices(teamName: String, Services: List[Service])

  case class Repository(name: String, url: String, isMicroservice: Boolean = false)

  case class Link(name: String, url: String)

  case class Environment(name: String, subEnvironments: Set[SubEnvironment])

  case class SubEnvironment(name: String)

  case class Service(
                      name: String,
                      githubUrl: Link,
                      ci: List[Link])

  case class DecoratedTeam(name: String, repositories: List[Repository])


  object Link {
    implicit val formats = Json.format[Link]
  }

  object Service {

    implicit val formats = Json.format[Service]

    def fromRepository(repository: Repository)(implicit urlTemplates: UrlTemplates): Option[Service] = {
      if (!repository.isMicroservice) None
      else Some(
        Service(
          repository.name,
          Link("github", repository.url),
          buildCiUrls(repository.url, urlTemplates)
        )
      )
    }

    private def buildCiUrls(repositoryUrl: String, urlTemplates: UrlTemplates): List[Link] = {

      val serviceName = repositoryUrl.split('/').last

      def buildUrls(templates: Seq[UrlTemplate]) = templates.map(t => Link(t.name, t.url(serviceName))).toList

      repositoryUrl.startsWith("https://github.com/") match {
        case true => buildUrls(urlTemplates.ciOpen)
        case false => buildUrls(urlTemplates.ciClosed)
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