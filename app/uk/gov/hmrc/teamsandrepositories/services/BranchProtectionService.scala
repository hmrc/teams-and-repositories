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