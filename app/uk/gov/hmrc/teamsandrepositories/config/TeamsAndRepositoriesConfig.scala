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

package uk.gov.hmrc.teamsandrepositories.config

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Json

case class UrlTemplates(ciClosed: Seq[UrlTemplate], ciOpen: Seq[UrlTemplate], environments:Map[String, Seq[UrlTemplate]])

case class UrlTemplate(name: String, displayName: String, template: String) {
  def url(serviceName : String) = template.replace("$name", serviceName)
}

object UrlTemplate {
  implicit val formats = Json.format[UrlTemplate]
}

@Singleton
class UrlTemplatesProvider @Inject()(configuration:Configuration) {

  val ciUrlTemplates: UrlTemplates = {
    configuration.getConfig("url-templates").map {
      config =>
        val openConfigs = getTemplatesForConfig("ci-open")
        val closedConfigs = getTemplatesForConfig("ci-closed")
        val envConfigs = getTemplatesForEnvironments

        UrlTemplates(closedConfigs, openConfigs, envConfigs)
    }.getOrElse(throw new RuntimeException("no url-templates config found"))
  }

  private def urlTemplates = {
    configuration.getConfig("url-templates").getOrElse(throw new RuntimeException("no url-templates config found"))
  }

  private def getTemplatesForEnvironments: Map[String, Seq[UrlTemplate]] = {
    val configs = urlTemplates.getConfigSeq("envrionments")
      .getOrElse(throw new RuntimeException("incorrect environment configuration"))

    configs.map { cf =>
      val envName = cf.getString("name")
        .getOrElse(throw new RuntimeException("incorrect environment configuration"))

      val envTemplates = cf.getConfigSeq("services")
        .getOrElse(throw new RuntimeException("incorrect environment configuration"))
        .map { s => readLink(s) }
      envName -> envTemplates.toSeq.flatten
    }.toMap
  }

  private def getTemplatesForConfig(path: String) = {
    val configs = urlTemplates.getConfigSeq(path)
    require(configs.exists(_.nonEmpty), s"no $path config found")

    configs.get.flatMap { config =>
      readLink(config)
    }.distinct
  }

  private def readLink(config:Configuration):Option[UrlTemplate]={
    for {
      name <- config.getString("name")
      displayName <- config.getString("display-name")
      url <- config.getString("url")
    } yield UrlTemplate(name, displayName, url)
  }
}
