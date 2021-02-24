/*
 * Copyright 2021 HM Revenue & Customs
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

import java.io.File

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.{ConfigList, ConfigObject}
import play.api.Configuration
import uk.gov.hmrc.githubclient.GitApiConfig

import scala.collection.JavaConverters._


@Singleton
class GithubConfig @Inject()(configuration: Configuration) {
  private val host = configuration.getOptional[String]("github.open.api.host")
  private val user = configuration.getOptional[String]("github.open.api.user")
  private val key  = configuration.getOptional[String]("github.open.api.key")

  val githubApiOpenConfig: GitApiConfig =
    (user, key, host) match {
      case (Some(u), Some(k), Some(h)) => GitApiConfig(u, k, h)
      case (None, None, None) if new File(gitPath(".credentials")).exists() => GitApiConfig.fromFile(gitPath(".credentials"))
      case _ => GitApiConfig("user_not_set", "key_not_set", "https://hostnotset.com")
    }

  val hiddenRepositories: List[String] =
    configuration.getOptional[String]("github.hidden.repositories")
      .fold(List.empty[String])(_.split(",").toList)

  val hiddenTeams: List[String] =
    configuration.getOptional[String]("github.hidden.teams")
      .fold(List.empty[String])(_.split(",").toList)

  private def gitPath(gitFolder: String): String =
    s"${System.getProperty("user.home")}/.github/$gitFolder"


  val url    = configuration.get[String]("github.open.api.url")
  val rawUrl = configuration.get[String]("github.open.api.rawurl")

  val tokens: List[(String, String)] =
    configuration.get[ConfigList]("ratemetrics.githubtokens").asScala.toList
      .map(cv => new Configuration(cv.asInstanceOf[ConfigObject].toConfig))
      .flatMap { config =>
        for {
          username <- config.getOptional[String]("username")
          token    <- config.getOptional[String]("token")
        } yield (username, token)
      }
}
