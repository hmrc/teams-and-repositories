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
import uk.gov.hmrc.githubclient.GitApiConfig



class GithubConfig(configuration: Configuration) {
  val githubOpenConfigKey = "github.open.api"
  val githubEnterpriseConfigKey = "github.enterprise.api"
  val githubHiddenRepositoriesConfigKey = "github.hidden.repositories"
  val githubHiddenTeamsConfigKey = "github.hidden.teams"

  val githubApiOpenConfig =
    getGitApiConfig(githubOpenConfigKey).getOrElse(GitApiConfig.fromFile(gitPath(".credentials")))

  val githubApiEnterpriseConfig =
    getGitApiConfig(githubEnterpriseConfigKey).getOrElse(GitApiConfig.fromFile(gitPath(".githubenterprise")))


  val hiddenRepositories = configuration.getString(githubHiddenRepositoriesConfigKey).fold(List.empty[String])(x => x.split(",").toList)

  val hiddenTeams = configuration.getString(githubHiddenTeamsConfigKey).fold(List.empty[String])(x => x.split(",").toList)

  private def gitPath(gitFolder: String): String =
    s"${System.getProperty("user.home")}/.github/$gitFolder"

  private def getGitApiConfig(base: String): Option[GitApiConfig] =
    for {
      host <- configuration.getString(s"$base.host")
      user <- configuration.getString(s"$base.user")
      key <- configuration.getString(s"$base.key")
    } yield GitApiConfig(user, key, host)


}

