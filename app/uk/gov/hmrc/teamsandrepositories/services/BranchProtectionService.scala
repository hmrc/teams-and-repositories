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

package uk.gov.hmrc.teamsandrepositories.services

import cats.data.OptionT
import uk.gov.hmrc.teamsandrepositories.connectors.{BranchProtectionApiConnector, GhRepository, GithubConnector}
import uk.gov.hmrc.teamsandrepositories.models.NoSuchRepository
import uk.gov.hmrc.teamsandrepositories.persistence.RepositoriesPersistence

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BranchProtectionService @Inject()(
  branchProtectionApiConnector: BranchProtectionApiConnector,
  githubConnector: GithubConnector,
  repositoriesPersistence: RepositoriesPersistence
) {

  def setBranchProtection(repoName: String)(implicit ec: ExecutionContext): Future[Unit] =
    for {
        _ <- branchProtectionApiConnector.setBranchProtection(repoName)
        r <- OptionT(githubConnector.getRepo(repoName)).getOrElseF(Future.failed[GhRepository](NoSuchRepository(repoName)))
        _ <- repositoriesPersistence.updateRepoBranchProtection(repoName, r.branchProtection)
    } yield ()
}