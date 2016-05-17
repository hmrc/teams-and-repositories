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

package uk.gov.hmrc.teamsandservices.config

import play.api.Play
import uk.gov.hmrc.githubclient.GitApiConfig

trait GithubConfigProvider {
  def githubConfig: GithubConfig = GithubConfig
}

trait GithubConfig {
  def hiddenRepositories: List[String]
  def hiddenTeams: List[String]
}

object GithubConfig extends GithubConfig {
  val githubOpenConfigKey = "github.open.api"
  val githubEnterpriseConfigKey = "github.enterprise.api"
  val githubHiddenRepositoriesConfigKey = "github.hidden.repositories"
  val githubHiddenTeamsConfigKey = "github.hidden.teams"

  private val gitOpenConfig = (key: String) => config(s"$githubOpenConfigKey.$key")
  private val gitEnterpriseConfig = (key: String) => config(s"$githubEnterpriseConfigKey.$key")

  lazy val githubApiOpenConfig = option(gitOpenConfig).getOrElse(GitApiConfig.fromFile(s"${System.getProperty("user.home")}/.github/.credentials"))
  lazy val githubApiEnterpriseConfig = option(gitEnterpriseConfig).getOrElse(GitApiConfig.fromFile(s"${System.getProperty("user.home")}/.github/.githubenterprise"))
  lazy val hiddenRepositories = config(githubHiddenRepositoriesConfigKey).fold(List.empty[String])(x => x.split(",").toList)
  lazy val hiddenTeams = config(githubHiddenTeamsConfigKey).fold(List.empty[String])(x => x.split(",").toList)

  private def config(path: String) = Play.current.configuration.getString(s"$path")
  private def option(config: String => Option[String]): Option[GitApiConfig] =
    for {
      host <- config("host")
      user <- config("user")
      key <- config("key")
    } yield GitApiConfig(user, key, host)
}
