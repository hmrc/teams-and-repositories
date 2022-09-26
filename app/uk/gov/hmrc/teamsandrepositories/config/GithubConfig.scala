/*
 * Copyright 2022 HM Revenue & Customs
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
import com.typesafe.config.{ConfigList, ConfigObject}
import play.api.Configuration
import scala.concurrent.duration.Duration

import scala.collection.JavaConverters._


@Singleton
class GithubConfig @Inject()(configuration: Configuration) {
  val user   = configuration.get[String]("github.open.api.user")
  val key    = configuration.get[String]("github.open.api.key")
  val apiUrl = configuration.get[String]("github.open.api.url")
  val rawUrl = configuration.get[String]("github.open.api.rawurl")

  val hiddenRepositories: Set[String] =
    configuration.getOptional[String]("github.hidden.repositories")
      .fold(Set.empty[String])(_.split(",").toSet)

  val hiddenTeams: Set[String] =
    configuration.getOptional[String]("github.hidden.teams")
      .fold(Set.empty[String])(_.split(",").toSet)

  val retryCount        = configuration.get[Int]("github.retry.count")
  val retryInitialDelay = configuration.get[Duration]("github.retry.initialDelay")

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
