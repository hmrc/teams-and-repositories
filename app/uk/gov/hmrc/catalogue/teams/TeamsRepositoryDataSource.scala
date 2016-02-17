package uk.gov.hmrc.catalogue.teamsrepository

import uk.gov.hmrc.catalogue.github._
import uk.gov.hmrc.catalogue.teams.ViewModels.{Repository, Team}

import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

trait TeamsRepositoryDataSource {
  def getTeamRepoMapping: Future[List[Team]]
}

trait GithubV3TeamsRepositoryDataSource extends TeamsRepositoryDataSource {
  def gh: GithubV3ApiClient

  def getTeamRepoMapping: Future[List[Team]] = {
    val orgs = gh.getOrganisations
    val teams = getOrgTeams(orgs)

    teams.flatMap {
      teams => Future.sequence {
        teams.par.map {
          t => gh.getReposForTeam(t).map(
            repos => Team(t.name, repos.map(
              y => Repository(y.name, y.html_url))))
        }.toList
      }
    }
  }

  private def getOrgTeams(orgsF: Future[List[GhOrganization]]): Future[List[GhTeam]] = {
    orgsF.flatMap {
      orgs => Future.sequence {
        orgs.par.map { gh.getTeamsForOrganisation }.toList
      }
    }.map(_.flatten)
  }
}

object GithubOpenTeamsRepositoryDataSource extends GithubV3TeamsRepositoryDataSource {
  val gh = new GithubV3ApiClient with GithubOpenApiEndpoints with GithubCredentials
}

object GithubEnterpriseTeamsRepositoryDataSource extends GithubV3TeamsRepositoryDataSource {
  val gh = new GithubV3ApiClient with GithubEnterpriseApiEndpoints with GithubCredentials
}

object CompositeTeamsRepositoryDataSource extends TeamsRepositoryDataSource {
  val dataSource = List(GithubEnterpriseTeamsRepositoryDataSource, GithubOpenTeamsRepositoryDataSource)

  override def getTeamRepoMapping: Future[List[Team]] = ???
}
