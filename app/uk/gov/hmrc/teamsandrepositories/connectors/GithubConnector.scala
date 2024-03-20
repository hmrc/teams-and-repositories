/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.teamsandrepositories.connectors

import com.codahale.metrics.MetricRegistry

import java.time.Instant
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.teamsandrepositories.models.{GitRepository, RepoType, ServiceType, Tag}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connectors.GhRepository.{ManifestDetails, RepoTypeHeuristics}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class GithubConnector @Inject()(
  githubConfig  : GithubConfig,
  httpClientV2  : HttpClientV2,
  metricRegistry: MetricRegistry,
)(implicit ec: ExecutionContext) {

  import GithubConnector._

  private val authHeader = "Authorization" -> s"token ${githubConfig.key}"
  private val acceptsHeader = "Accepts" -> "application/vnd.github.v3+json"

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  def getTeams(): Future[List[GhTeam]] =
    withCounter(s"github.open.teams") {
      val root =
        __ \ "data" \ "organization" \ "teams"

      implicit val reads =
        (root \ "nodes").read(Reads.list(GhTeam.reads))

      executePagedGqlQuery(
        query = getTeamsQuery,
        cursorPath = root \ "pageInfo" \ "endCursor"
      ).map(_.flatten)
    }

  def getTeams(repoName: String): Future[List[String]] = {
    implicit val reads =
      (__ \ "name").read[String]

    httpClientV2
      .get(url"${githubConfig.apiUrl}/repos/hmrc/$repoName/teams")
      .setHeader(authHeader)
      .withProxy
      .execute[List[String]]
  }

  def getReposForTeam(team: GhTeam): Future[List[GhRepository]] =
    withCounter("github.open.repos") {
      val root =
        __ \ "data" \ "organization" \ "team" \ "repositories"

      implicit val reads =
        (root \ "edges").readWithDefault(List.empty[GhRepositoryWithPermission])(Reads.list(GhRepositoryWithPermission.reads))

      executePagedGqlQuery[List[GhRepositoryWithPermission]](
        query = getReposForTeamQuery.withVariable("team", JsString(team.githubSlug)),
        cursorPath = root \ "pageInfo" \ "endCursor"
      ).map(_.flatten.filter(_.permission == "WRITE").map(_.ghRepository))
    }

  def getRepos(): Future[List[GhRepository]] =
    withCounter("github.open.repos") {
      val root =
        __ \ "data" \ "organization" \ "repositories"

      implicit val reads =
        (root \ "nodes").read(Reads.list(GhRepository.reads))

      executePagedGqlQuery[List[GhRepository]](
        query = getReposQuery,
        cursorPath = root \ "pageInfo" \ "endCursor",
      ).map(_.flatten)
    }

  def getRepo(repoName: String): Future[Option[GhRepository]] = {
    implicit val reads =
      (__ \ "data" \ "organization" \ "repository")
        .readNullable(GhRepository.reads)

    executeGqlQuery[Option[GhRepository]](getRepoQuery.withVariable("repo", JsString(repoName)))
  }

  def getRateLimitMetrics(token: String, resource: RateLimitMetrics.Resource): Future[RateLimitMetrics] = {
    implicit val rlmr = RateLimitMetrics.reads(resource)
    httpClientV2
      .get(url"${githubConfig.apiUrl}/rate_limit")
      .setHeader("Authorization" -> s"token $token")
      .withProxy
      .execute[RateLimitMetrics]
  }

  private def executeGqlQuery[A](
    query: GraphqlQuery
  )(implicit
    reads: Reads[A],
    mf   : Manifest[A]
  ): Future[A] =
    httpClientV2
      .post(url"${githubConfig.apiUrl}/graphql")
      .withBody(query.asJson)
      .setHeader(authHeader)
      .setHeader(acceptsHeader)
      .withProxy
      .execute[A]

  private def executePagedGqlQuery[A](
    query     : GraphqlQuery,
    cursorPath: JsPath
  )(implicit
    reads: Reads[A],
    mf   : Manifest[A]
  ): Future[List[A]] = {
    implicit val readsWithCursor: Reads[WithCursor[A]] =
      (cursorPath.readNullable[String] ~ reads)(WithCursor(_, _))

    for {
      response <- executeGqlQuery[WithCursor[A]](query)
      recurse  <- response.cursor.fold(Future.successful(List(response.value))) { cursor =>
                    executePagedGqlQuery[A](query.withVariable("cursor", JsString(cursor)), cursorPath)
                      .map(response.value :: _)
                  }
    } yield recurse
  }

  private case class WithCursor[A](
    cursor: Option[String],
    value: A
  )

  def withCounter[T](name: String)(f: Future[T]) =
    f.andThen {
      case Success(_) =>
        metricRegistry.counter(s"$name.success").inc()
      case Failure(_) =>
        metricRegistry.counter(s"$name.failure").inc()
    }

  private case class GhRepositoryWithPermission (
    permission  : String,
    ghRepository: GhRepository
  )

  private object GhRepositoryWithPermission {
    val reads: Reads[GhRepositoryWithPermission] =
      ( (__ \ "permission").read[String]
        ~ (__ \ "node"      ).read[GhRepository](GhRepository.reads)
        )(apply _)
  }
}

object GithubConnector {

  final case class GraphqlQuery(
    query    : String,
    variables: Map[String, JsValue] = Map.empty
  ) {

    def withVariable(name: String, value: JsValue): GraphqlQuery =
      copy(variables = variables + (name -> value))

    def asJson: JsValue =
      JsObject(
        Map(
          "query"     -> JsString(query),
          "variables" -> JsObject(variables)
        )
      )

    def asJsonString: String =
      Json.stringify(asJson)
  }

  private val repositoryFields =
    """
      name
      url
      isFork
      createdAt
      pushedAt
      isPrivate
      primaryLanguage {
        name
      }
      isArchived
      defaultBranchRef {
        name
        branchProtectionRule {
          requiresApprovingReviews
          dismissesStaleReviews
          requiresCommitSignatures
        }
      }
      repositoryYaml: object(expression: "HEAD:repository.yaml") {
        ... on Blob {
          text
        }
      }
      hasApplicationConf: object(expression: "HEAD:conf/application.conf") {
        id
      }
      hasDeployProperties: object(expression: "HEAD:deploy.properties") {
        id
      }
      hasProcfile: object(expression: "HEAD:Procfile") {
        id
      }
      hasSrcMainScala: object(expression: "HEAD:src/main/scala") {
        id
      }
      hasSrcMainJava: object(expression: "HEAD:src/main/java") {
        id
      }
      tags: refs(refPrefix: "refs/tags/") {
        totalCount
      }
    """

  val getReposForTeamQuery: GraphqlQuery =
    GraphqlQuery(
      s"""
        query($$team: String!, $$cursor: String) {
          organization(login: "hmrc") {
            team(slug: $$team) {
              repositories(first: 30, after: $$cursor, orderBy: {field: CREATED_AT, direction: ASC}) {
                pageInfo {
                  endCursor
                }
                edges {
                  permission
                  node {
                    $repositoryFields
                  }
                }
              }
            }
          }
        }
      """
    )

  val getReposQuery: GraphqlQuery =
    GraphqlQuery(
      s"""
        query($$cursor: String) {
          organization(login: "hmrc") {
            repositories(first: 30, after: $$cursor, orderBy: {field: CREATED_AT, direction: ASC}) {
              pageInfo {
                endCursor
              }
              nodes {
                $repositoryFields
              }
            }
          }
        }
      """
    )

  val getRepoQuery: GraphqlQuery =
    GraphqlQuery(
      s"""
        query($$repo: String!) {
          organization(login: "hmrc") {
            repository(name: $$repo) {
              $repositoryFields
            }
          }
        }
      """
    )

  val getTeamsQuery: GraphqlQuery =
    GraphqlQuery(
      """
        query ($cursor: String) {
          organization(login: "hmrc") {
            teams(first: 50, after: $cursor, orderBy: {field: NAME, direction: ASC}) {
              pageInfo {
                endCursor
              }
              nodes {
                name
                createdAt
              }
            }
          }
        }
      """
    )
}

case class GhTeam(
  name     : String,
  createdAt: Instant
) {
  def githubSlug: String =
    name.replaceAll(" - | |\\.", "-").toLowerCase
}

object GhTeam {

  val reads: Reads[GhTeam] =
    ( (__ \ "name"     ).read[String]
    ~ (__ \ "createdAt").read[Instant]
    )(apply _)
}

case class GhRepository(
  name              : String,
  htmlUrl           : String,
  fork              : Boolean,
  createdDate       : Instant,
  pushedAt          : Instant,
  isPrivate         : Boolean,
  language          : Option[String],
  isArchived        : Boolean,
  defaultBranch     : String,
  branchProtection  : Option[BranchProtection],
  repositoryYamlText: Option[String],
  repoTypeHeuristics: RepoTypeHeuristics
) {

  def toGitRepository: GitRepository = {
    val manifestDetails: ManifestDetails =
      repositoryYamlText
        .flatMap(ManifestDetails.parse(name, _))
        .getOrElse(
          ManifestDetails(
            repoType             = None,
            serviceType          = None,
            tags                 = None,
            digitalServiceName   = None,
            description          = None,
            owningTeams          = Seq.empty,
            isDeprecated         = false,
            prototypeName        = None,
            prototypeAutoPublish = None
          )
        )

    val repoType: RepoType =
      manifestDetails
        .repoType
        .getOrElse(repoTypeHeuristics.inferredRepoType)

    val prototypeName: Option[String] =
      Option.when(repoType == RepoType.Prototype)(
        manifestDetails
          .prototypeName
          .getOrElse(name) // default to repository name if not defined in repository.yaml
      )

    GitRepository(
      name                 = name,
      description          = manifestDetails.description.getOrElse(""),
      url                  = htmlUrl,
      createdDate          = createdDate,
      lastActiveDate       = pushedAt,
      isPrivate            = isPrivate,
      repoType             = repoType,
      serviceType          = manifestDetails.serviceType,
      tags                 = manifestDetails.tags,
      digitalServiceName   = manifestDetails.digitalServiceName,
      owningTeams          = manifestDetails.owningTeams.sorted,
      language             = language,
      isArchived           = isArchived,
      defaultBranch        = defaultBranch,
      branchProtection     = branchProtection,
      isDeprecated         = manifestDetails.isDeprecated,
      prototypeName        = prototypeName,
      prototypeAutoPublish = manifestDetails.prototypeAutoPublish,
      repositoryYamlText   = repositoryYamlText
    )
  }
}

object GhRepository {

  final case class ManifestDetails(
    repoType            : Option[RepoType],
    serviceType         : Option[ServiceType],
    tags                : Option[Set[Tag]],
    digitalServiceName  : Option[String],
    description         : Option[String],
    owningTeams         : Seq[String],
    isDeprecated        : Boolean = false,
    prototypeName       : Option[String],
    prototypeAutoPublish: Option[Boolean]
  )

  object ManifestDetails {
    import uk.gov.hmrc.teamsandrepositories.util.YamlMap

    private val logger = Logger(this.getClass)

    def parse(repoName: String, repositoryYaml: String): Option[ManifestDetails] =
      YamlMap.parse(repositoryYaml) match {
        case Failure(exception) =>
          logger.warn(s"repository.yaml for $repoName is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
          None
        case Success(config) =>
          val manifestDetails = ManifestDetails(
            repoType             = RepoType.parse(config.get[String]("type").getOrElse("")).toOption
          , serviceType          = ServiceType.parse(config.get[String]("service-type").getOrElse("")).toOption
          , tags                 = config.getArray("tags").map(_.flatMap(str => Tag.parse(str).toOption).toSet)
          , digitalServiceName   = config.get[String]("digital-service")
          , description          = config.get[String]("description")
          , owningTeams          = config.getArray("owning-teams").getOrElse(Nil)
          , isDeprecated         = config.get[Boolean]("deprecated").getOrElse(false)
          , prototypeName        = config.get[String]("prototype-name")
          , prototypeAutoPublish = config.get[Boolean]("prototype-auto-publish")
          )
          logger.info(s"ManifestDetails for repo: $repoName is $manifestDetails, parsed from repository.yaml: $repositoryYaml")
          Some(manifestDetails)
      }
  }

  final case class RepoTypeHeuristics(
    prototypeInName    : Boolean,
    testsInName        : Boolean,
    hasApplicationConf : Boolean,
    hasDeployProperties: Boolean,
    hasProcfile        : Boolean,
    hasSrcMainScala    : Boolean,
    hasSrcMainJava     : Boolean,
    hasTags            : Boolean
  ) {

    def inferredRepoType: RepoType = {
      if (prototypeInName)
        RepoType.Prototype
      else if (testsInName)
        RepoType.Test
      else if (hasApplicationConf || hasDeployProperties || hasProcfile)
        RepoType.Service
      else if ((hasSrcMainScala || hasSrcMainJava) && hasTags)
        RepoType.Library
      else
        RepoType.Other
    }
  }

  object RepoTypeHeuristics {
    val reads: Reads[RepoTypeHeuristics] =
      ( (__ \ "name"                      ).read[String].map(_.endsWith("-prototype"))
      ~ (__ \ "name"                      ).read[String].map(name => name.endsWith("-tests") || name.endsWith("-test"))
      ~ (__ \ "hasApplicationConf" \ "id" ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasDeployProperties" \ "id").readNullable[String].map(_.isDefined)
      ~ (__ \ "hasProcfile" \ "id"        ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasSrcMainScala" \ "id"    ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasSrcMainJava" \ "id"     ).readNullable[String].map(_.isDefined)
      ~ (__ \ "tags" \ "totalCount"       ).readNullable[Int].map(_.exists(_ > 0))
      )(apply _)
  }

  val reads: Reads[GhRepository] =
    ( (__ \ "name"                                     ).read[String]
    ~ (__ \ "url"                                      ).read[String]
    ~ (__ \ "isFork"                                   ).read[Boolean]
    ~ (__ \ "createdAt"                                ).read[Instant]
    ~ (__ \ "pushedAt"                                 ).readWithDefault(Instant.MIN)
    ~ (__ \ "isPrivate"                                ).read[Boolean]
    ~ (__ \ "primaryLanguage" \ "name"                 ).readNullable[String]
    ~ (__ \ "isArchived"                               ).read[Boolean]
    ~ (__ \ "defaultBranchRef" \ "name"                ).readWithDefault("main")
    ~ (__ \ "defaultBranchRef" \ "branchProtectionRule").readNullable(BranchProtection.format)
    ~ (__ \ "repositoryYaml" \ "text"                  ).readNullable[String]
    ~ RepoTypeHeuristics.reads
    )(apply _)
}

case class RateLimitMetrics(
  limit    : Int,
  remaining: Int,
  reset    : Int
)

object RateLimitMetrics {

  sealed trait Resource {
    def asString: String
  }

  object Resource {
    final case object Core extends Resource { val asString = "core" }
    final case object GraphQl extends Resource { val asString = "graphql" }
  }

  def reads(resource: Resource): Reads[RateLimitMetrics] =
    Reads.at(__ \ "resources" \ resource.asString)(
      ( (__ \ "limit"    ).read[Int]
      ~ (__ \ "remaining").read[Int]
      ~ (__ \ "reset"    ).read[Int]
      )(RateLimitMetrics.apply _)
    )
}

case class ApiRateLimitExceededException(
  exception: Throwable
) extends RuntimeException(exception)

case class ApiAbuseDetectedException(
  exception: Throwable
) extends RuntimeException(exception)

object RateLimit {
  val logger: Logger = Logger(getClass)

  def convertRateLimitErrors[A]: PartialFunction[Throwable, Future[A]] = {
    case e if e.getMessage.toLowerCase.contains("api rate limit exceeded")
           || e.getMessage.toLowerCase.contains("have exceeded a secondary rate limit") =>
      logger.error("=== Api rate limit has been reached ===", e)
      Future.failed(ApiRateLimitExceededException(e))

    case e if e.getMessage.toLowerCase.contains("triggered an abuse detection mechanism") =>
      logger.error("=== Api abuse detected ===", e)
      Future.failed(ApiAbuseDetectedException(e))
  }
}

final case class BranchProtection(
  requiresApprovingReviews: Boolean,
  dismissesStaleReview    : Boolean,
  requiresCommitSignatures: Boolean
)

object BranchProtection {

  val format: Format[BranchProtection] =
    ( (__ \ "requiresApprovingReviews").format[Boolean]
    ~ (__ \ "dismissesStaleReviews"   ).format[Boolean]
    ~ (__ \ "requiresCommitSignatures").format[Boolean]
    )(apply, unlift(unapply))
}
