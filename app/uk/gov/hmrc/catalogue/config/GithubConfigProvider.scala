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

import java.io.File

import play.api.Play

trait GithubConfigProvider {
  def githubOpen: GithubCredentials
  def githubEnterprise: GithubCredentials
}

object GithubConfigProvider extends GithubConfigProvider with CredentialsFinder {
  val githubOpenConfigKey = s"github.open.api"
  val githubEnterpriseConfigKey = s"github.enterprise.api"

  def githubOpen = fallBackToFileSystem(".credentials", GithubCredentials(
    config(s"$githubOpenConfigKey.host"),
    config(s"$githubOpenConfigKey.user"),
    config(s"$githubOpenConfigKey.key")))

  def githubEnterprise = fallBackToFileSystem(".githubenterprise", GithubCredentials(
    config(s"$githubEnterpriseConfigKey.host"),
    config(s"$githubEnterpriseConfigKey.user"),
    config(s"$githubEnterpriseConfigKey.key")))

  private def fallBackToFileSystem(filename: String, credentials: GithubCredentials) = {
    def isNullOrEmpty(s: String) = s != null && s.isEmpty

    if (isNullOrEmpty(credentials.host) || isNullOrEmpty(credentials.user))
      new File(System.getProperty("user.home"), ".github").listFiles()
        .filter { f => f.getName == filename  }
        .flatMap { c => findGithubCredsInFile(c.toPath) }.head
    else credentials
  }

  private def config(path: String) = Play.current.configuration.getString(s"$path").getOrElse("")

}
