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

import play.api.Play
import uk.gov.hmrc.catalogue.config.ConfigFile

trait GithubConfigProvider {
  def githubConfig: GithubConfig = GithubConfig
}

trait GithubConfig {
  def repositoryBlacklist: List[String]
}

object GithubConfig extends GithubConfig {
  val githubOpenConfigKey = "github.open.api"
  val githubEnterpriseConfigKey = "github.enterprise.api"
  val githubRepositoryBlacklistConfigKey = "github.repositoryBlacklist"

  def githubOpen = fallBackToFileSystem(".credentials", GithubCredentials(
    config(s"$githubOpenConfigKey.host"),
    config(s"$githubOpenConfigKey.user"),
    config(s"$githubOpenConfigKey.key")))

  def githubEnterprise = fallBackToFileSystem(".githubenterprise", GithubCredentials(
    config(s"$githubEnterpriseConfigKey.host"),
    config(s"$githubEnterpriseConfigKey.user"),
    config(s"$githubEnterpriseConfigKey.key")))

  def repositoryBlacklist = config(githubRepositoryBlacklistConfigKey).split(",").toList

  private def fallBackToFileSystem(filename: String, credentials: GithubCredentials) = {
    def isNullOrEmpty(s: String) = s != null && s.isEmpty

    if (isNullOrEmpty(credentials.host) || isNullOrEmpty(credentials.user))
      new File(System.getProperty("user.home"), ".github").listFiles()
        .filter { f => f.getName == filename  }
        .flatMap { c => findGithubCredsInFile(c.toPath) }.head
    else credentials
  }

  private def findGithubCredsInFile(file:Path):Option[GithubCredentials] = {
    val conf = new ConfigFile(file)

    for( user <- conf.get("user");
         token <- conf.get("token");
         host <- conf.get("host")
    ) yield GithubCredentials(host, user, token)
  }

  private def config(path: String) = Play.current.configuration.getString(s"$path").getOrElse("")

}
