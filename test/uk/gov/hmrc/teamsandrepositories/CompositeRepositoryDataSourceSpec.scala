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

package uk.gov.hmrc.teamsandrepositories

import java.time.LocalDateTime
import java.util.Date

import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.githubclient.{GitApiConfig, GithubApiClient}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.persitence.{MongoConnector, TeamsAndReposPersister}
import uk.gov.hmrc.teamsandrepositories.persitence.model.TeamRepositories
import uk.gov.hmrc.teamsandrepositories.services.{GitCompositeDataSource, GithubApiClientDecorator, GithubV3RepositoryDataSource}

import scala.concurrent.{ExecutionContext, Future}

class CompositeRepositoryDataSourceSpec extends WordSpec with MockitoSugar with ScalaFutures with Matchers with DefaultPatienceConfig {

  val now = new Date().getTime

  "Retrieving team repo mappings" should {

    "return the combination of all input sources" in {

      val teamsList1 = List(
        TeamRepositories("A", List(GitRepository("A_r", "Some Description", "url_A", now, now))),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now))),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now))))

      val teamsList2 = List(
        TeamRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now))),
        TeamRepositories("E", List(GitRepository("E_r", "Some Description", "url_E", now, now))),
        TeamRepositories("F", List(GitRepository("F_r", "Some Description", "url_F", now, now))))

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(Future.successful(teamsList1))

      val dataSource2 = mock[GithubV3RepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(Future.successful(teamsList2))

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2)
      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      result.length shouldBe 6
      result should contain(teamsList1.head)
      result should contain(teamsList1(1))
      result should contain(teamsList1(2))
      result should contain(teamsList2.head)
      result should contain(teamsList2(1))
      result should contain(teamsList2(2))
    }

    "combine teams that have the same names in both sources and sort repositories alphabetically" in {

      val repoAA = GitRepository("A_A", "Some Description", "url_A_A", now, now)
      val repoAB = GitRepository("A_B", "Some Description", "url_A_B", now, now)
      val repoAC = GitRepository("A_C", "Some Description", "url_A_C", now, now)

      val teamsList1 = List(
        TeamRepositories("A", List(repoAC, repoAB)),
        TeamRepositories("B", List(GitRepository("B_r", "Some Description", "url_B", now, now))),
        TeamRepositories("C", List(GitRepository("C_r", "Some Description", "url_C", now, now))))

      val teamsList2 = List(
        TeamRepositories("A", List(repoAA)),
        TeamRepositories("D", List(GitRepository("D_r", "Some Description", "url_D", now, now))))

      val dataSource1 = mock[GithubV3RepositoryDataSource]
      when(dataSource1.getTeamRepoMapping).thenReturn(Future.successful(teamsList1))

      val dataSource2 = mock[GithubV3RepositoryDataSource]
      when(dataSource2.getTeamRepoMapping).thenReturn(Future.successful(teamsList2))

      val compositeDataSource = buildCompositeDataSource(dataSource1, dataSource2)

      val result = compositeDataSource.persistTeamRepoMapping.futureValue

      result.length shouldBe 4
      result.find(_.teamName == "A").get.repositories should contain inOrderOnly(
        repoAA, repoAB, repoAC)

      result should contain(teamsList1(1))
      result should contain(teamsList1(2))
      result should contain(teamsList2(1))

    }
  }

  private def buildCompositeDataSource(dataSource1: GithubV3RepositoryDataSource,
                                       dataSource2: GithubV3RepositoryDataSource) = {

    val githubConfig = mock[GithubConfig]
    val persister = mock[TeamsAndReposPersister]
    val connector = mock[MongoConnector]
    val githubClientDecorator = mock[GithubApiClientDecorator]

    val gitApiOpenConfig = mock[GitApiConfig]
    val gitApiEnterpriseConfig = mock[GitApiConfig]

    when(githubConfig.githubApiEnterpriseConfig).thenReturn(gitApiEnterpriseConfig)
    when(githubConfig.githubApiOpenConfig).thenReturn(gitApiOpenConfig)

    val enterpriseUrl = "enterprise.com"
    val enterpriseKey = "enterprise.key"
    when(gitApiEnterpriseConfig.apiUrl).thenReturn(enterpriseUrl)
    when(gitApiEnterpriseConfig.key).thenReturn(enterpriseKey)

    val openUrl = "open.com"
    val openKey = "open.key"
    when(gitApiOpenConfig.apiUrl).thenReturn(openUrl)
    when(gitApiOpenConfig.key).thenReturn(openKey)

    val enterpriseGithubClient = mock[GithubApiClient]
    val openGithubClient = mock[GithubApiClient]

    when(githubClientDecorator.githubApiClient(enterpriseUrl, enterpriseKey)).thenReturn(enterpriseGithubClient)
    when(githubClientDecorator.githubApiClient(openUrl, openKey)).thenReturn(openGithubClient)

    val repositories: Seq[TeamRepositories] = Seq(TeamRepositories("testTeam", Nil))
    when(persister.update(ArgumentMatchers.any())).thenAnswer(new Answer[Future[TeamRepositories]] {
      override def answer(invocation: InvocationOnMock): Future[TeamRepositories] = {
        val args = invocation.getArguments()
        Future.successful(args(0).asInstanceOf[TeamRepositories])
      }
    })
    when(persister.updateTimestamp(ArgumentMatchers.any())).thenReturn(Future.successful(true))

    new GitCompositeDataSource(githubConfig, persister, connector, githubClientDecorator) {
      override val dataSources: List[GithubV3RepositoryDataSource] = List(dataSource1, dataSource2)
    }
  }
}
