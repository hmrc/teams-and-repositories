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

package uk.gov.hmrc.teamsandrepositories.config

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{Json, Format}

import scala.collection.immutable.ListMap

case class UrlTemplates(
  environments: ListMap[String, Seq[UrlTemplate]]
)

case class UrlTemplate(
  name       : String,
  displayName: String,
  template   : String
):
  def url(serviceName: String): String =
    template.replace(s"$$name", serviceName)

object UrlTemplate:
  given Format[UrlTemplate] = Json.format[UrlTemplate]

@Singleton
class UrlTemplatesProvider @Inject()(
  configuration: Configuration
):

  val ciUrlTemplates: UrlTemplates =
    UrlTemplates(retrieveTemplatesForEnvironments())

  private def retrieveTemplatesForEnvironments(): ListMap[String, Seq[UrlTemplate]] =
    val envConfigs = configuration.get[Seq[Configuration]]("url-templates.envrionments")

    envConfigs.foldLeft(ListMap.empty[String, Seq[UrlTemplate]]){ (acc, envConfig) =>
      val envName      = envConfig.get[String]("name")
      val envTemplates = envConfig.get[Seq[Configuration]]("services").flatMap(readLink)
      acc + (envName -> envTemplates)
    }

  private def readLink(config: Configuration): Option[UrlTemplate] =
    for
      name        <- config.getOptional[String]("name")
      displayName <- config.getOptional[String]("display-name")
      url         <- config.getOptional[String]("url")
    yield UrlTemplate(name, displayName, url)
