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

package uk.gov.hmrc.catalogue.config

import play.api.libs.json.Json

case class UrlTemplates(ciClosed: Seq[UrlTemplate], ciOpen: Seq[UrlTemplate])

case class UrlTemplate(name: String, template: String) {

  def url(serviceName : String) = template.replace("$name", serviceName)
}

object UrlTemplate {
  implicit val formats = Json.format[UrlTemplate]

}

trait CatalogueConfig {


  implicit val ciUrlTemplates: UrlTemplates = {

    play.api.Play.current.configuration.getConfig("url-templates").map {
      config =>
        val openConfigs = getTemplatesForConfig("ci-open")
        val closedConfigs = getTemplatesForConfig("ci-closed")

        UrlTemplates(ciOpen = openConfigs, ciClosed = closedConfigs)
    }.getOrElse(throw new RuntimeException("no url-templates config found"))

  }


  private def urlTemplates = {
    play.api.Play.current.configuration.getConfig("url-templates").getOrElse(throw new RuntimeException("no url-templates config found"))
  }


  private def getTemplatesForConfig(path: String) = {
    val configs = urlTemplates.getConfigSeq(path)
    require(configs.exists(!_.isEmpty), s"no $path config found")

    configs.get.flatMap { config =>
      for {
        name <- config.getString("name")
        url <- config.getString("url")
      } yield UrlTemplate(name, url)
    }.toSet.toSeq

  }


}
