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

package uk.gov.hmrc.catalogue.github

import java.io.File
import java.nio.file.Path

import play.Logger
import play.api.Play
import uk.gov.hmrc.catalogue.config.ConfigFile

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


  lazy val githubOpenCredentials = credentials(gitOpenConfig).getOrElse(fromFileSystem(".credentials"))

  lazy val githubEnterpriseCredentials = credentials(gitEnterpriseConfig).getOrElse(fromFileSystem(".githubenterprise"))

  lazy val hiddenRepositories = config(githubHiddenRepositoriesConfigKey).fold(List.empty[String])(x => x.split(",").toList)

  lazy val hiddenTeams = config(githubHiddenTeamsConfigKey).fold(List.empty[String])(x => x.split(",").toList)

  private def fromFileSystem(filename: String): GithubCredentials = {

    Logger.info(s"Credentials not found in config, falling back to $filename")

    val credentialFile: Option[File] = new File(System.getProperty("user.home"), ".github").listFiles().find { f => f.getName == filename }

    credentialFile.flatMap(x => findGithubCredsInFile(x.toPath)).getOrElse(throw new RuntimeException(s"credential file : $filename not found"))

  }

  private def findGithubCredsInFile(file: Path): Option[GithubCredentials] = {
    val conf = new ConfigFile(file)

    for {
      user <- conf.get("user")
      token <- conf.get("token")
      host <- conf.get("host")
    } yield GithubCredentials(host, user, token)
  }

  private def config(path: String) = Play.current.configuration.getString(s"$path")

  private def credentials(config: String => Option[String]): Option[GithubCredentials] = GithubCredentials.option(config)

}
