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

package uk.gov.hmrc.teamsandrepositories.connector


import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import play.api.http.Status
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import play.api.libs.ws.writeableOf_JsValue
import play.api.{Logger, Logging}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, Retries, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.teamsandrepositories.config.GithubConfig
import uk.gov.hmrc.teamsandrepositories.connector.GhRepository.{ManifestDetails, RepoTypeHeuristics}
import uk.gov.hmrc.teamsandrepositories.model.*
import uk.gov.hmrc.teamsandrepositories.util.Parser

import java.time.Instant
import java.util.Date
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

@Singleton
class GithubConnector @Inject()(
  githubConfig              : GithubConfig,
  httpClientV2              : HttpClientV2,
  metricRegistry            : MetricRegistry,
  override val configuration: Config,
  override val actorSystem  : ActorSystem
)(using ExecutionContext) extends Retries with Logging:

  import GithubConnector.*

  private val authHeader    = "Authorization" -> s"token ${githubConfig.key}"
  private val acceptsHeader = "Accepts"       -> "application/vnd.github.v3+json"

  private given HeaderCarrier = HeaderCarrier()

  def getOpenPrs(): Future[Seq[OpenPullRequest]] =
    withCounter(s"github.open.teams"):
      val root: JsPath =
        __ \ "data" \ "organization" \ "repositories"

      given Reads[Seq[OpenPullRequest]] =
        (root \ "nodes").read(Reads.seq(OpenPullRequest.seqReads)).map(_.flatten)

      executePagedGqlQuery(
        query      = getOpenPrsQuery,
        cursorPath = root \ "pageInfo" \ "endCursor"
      ).map(_.flatten)

  def getOpenPrsForRepo(repoName: String): Future[Seq[OpenPullRequest]] =
    val root: JsPath =
      __ \ "data" \ "organization" \ "repository" \ "pullRequests"

    given Reads[Seq[OpenPullRequest]] =
      (root \ "nodes").readWithDefault(Seq.empty[OpenPullRequest])(Reads.seq(OpenPullRequest.prReads(repoName)))

    executePagedGqlQuery(
      query = getOpenPrsForRepoQuery.withVariable("repoName", JsString(repoName)),
      cursorPath = root \ "pageInfo" \ "endCursor"
    ).map(_.flatten)

  def getTeams(): Future[Seq[GhTeam]] =
    withCounter(s"github.open.teams"):
      val root: JsPath =
        __ \ "data" \ "organization" \ "teams"

      given Reads[Seq[GhTeam]] =
        (root \ "nodes").read(Reads.seq(GhTeam.reads))

      executePagedGqlQuery(
        query      = getTeamsQuery,
        cursorPath = root \ "pageInfo" \ "endCursor"
      ).map(_.flatten)

  def getTeams(repoName: String): Future[Seq[String]] =
    val stringReads: Reads[String] = (__ \ "name").read[String]
    given Reads[Seq[String]] = Reads.seq(stringReads)

    httpClientV2
      .get(url"${githubConfig.apiUrl}/repos/hmrc/$repoName/teams")
      .setHeader(authHeader)
      .withProxy
      .execute[Seq[String]]

  def getBranchProtectionRules(repoName: String, defaultBranch: String): Future[Option[BranchProtectionRules]] =
    given Reads[BranchProtectionRules] = BranchProtectionRules.reads
    httpClientV2
      .get(url"${githubConfig.apiUrl}/repos/hmrc/$repoName/branches/$defaultBranch/protection")
      .setHeader(authHeader)
      .setHeader("Accept" -> "application/vnd.github+json")
      .setHeader("X-GitHub-Api-Version" -> "2022-11-28")
      .withProxy
      .execute[Option[BranchProtectionRules]]

  def updateBranchProtectionRules(
    repoName     : String,
    defaultBranch: String,
    updatedRules : BranchProtectionRules
  ): Future[Unit] =
    given Writes[BranchProtectionRules] = BranchProtectionRules.writes
    val url = url"${githubConfig.apiUrl}/repos/hmrc/$repoName/branches/$defaultBranch/protection"
    httpClientV2
      .put(url)
      .setHeader(authHeader)
      .setHeader("X-GitHub-Api-Version" -> "2022-11-28")
      .withBody(Json.toJson(updatedRules))
      .withProxy
      .execute[HttpResponse]
      .flatMap: res =>
        if Status.isSuccessful(res.status) then
          Future.unit
        else Future.failed(sys.error(s"Call to $url failed with status: ${res.status}, body: ${res.body}"))

  def getReposForTeam(team: GhTeam): Future[Seq[GhRepository]] =
    withCounter("github.open.repos"):
      val root =
        __ \ "data" \ "organization" \ "team" \ "repositories"

      given Reads[Seq[GhRepository]] =
        (root \ "nodes").readWithDefault(Seq.empty[GhRepository])(Reads.seq(GhRepository.reads(githubConfig)))

      executePagedGqlQuery[Seq[GhRepository]](
        query      = getReposForTeamQuery.withVariable("team", JsString(team.githubSlug)),
        cursorPath = root \ "pageInfo" \ "endCursor"
      ).map(_.flatten)

  def getRepos(): Future[Seq[GhRepository]] =
    withCounter("github.open.repos"):
      val root =
        __ \ "data" \ "organization" \ "repositories"

      given Reads[Seq[GhRepository]] =
        (root \ "nodes").read(Reads.seq(GhRepository.reads(githubConfig)))

      executePagedGqlQuery[Seq[GhRepository]](
        query      = getReposQuery,
        cursorPath = root \ "pageInfo" \ "endCursor",
      ).map(_.flatten)

  def getRepo(repoName: String): Future[Option[GhRepository]] =
    given Reads[Option[GhRepository]] =
      (__ \ "data" \ "organization" \ "repository").readNullable(GhRepository.reads(githubConfig))

    executeGqlQuery[Option[GhRepository]](getRepoQuery.withVariable("repo", JsString(repoName)))

  def getRateLimitMetrics(token: String, resource: RateLimitMetrics.Resource): Future[RateLimitMetrics] =
    given Reads[RateLimitMetrics] = RateLimitMetrics.reads(resource)
    httpClientV2
      .get(url"${githubConfig.apiUrl}/rate_limit")
      .setHeader("Authorization" -> s"token $token")
      .withProxy
      .execute[RateLimitMetrics]

  private def executeGqlQuery[A : Reads : ClassTag](
    query: GraphqlQuery
  ): Future[A] =
    retryFor[A]("Github graphQL call") {
      case UpstreamErrorResponse.WithStatusCode(Status.BAD_GATEWAY) => true
    }{
      val startTime = Instant.now()
      httpClientV2
        .post(url"${githubConfig.apiUrl}/graphql")
        .withBody(query.asJson)
        .setHeader(authHeader)
        .setHeader(acceptsHeader)
        .withProxy
        .execute[A]
        .recoverWith:
          case ex @ UpstreamErrorResponse.WithStatusCode(Status.BAD_GATEWAY) =>
            val elapsed = java.time.Duration.between(startTime, Instant.now()).toMillis
            logger.warn(s"Failed GitHub GraphQL call took ${elapsed}ms. Error: ${ex.getMessage}")
            Future.failed(ex)
    }

  private def executePagedGqlQuery[A](
    query     : GraphqlQuery,
    cursorPath: JsPath
  )(using
    reads: Reads[A],
    ct: ClassTag[A]
  ): Future[List[A]] =
    given Reads[WithCursor[A]] =
      (cursorPath.readNullable[String] ~ reads)(WithCursor(_, _))

    for
      response <- executeGqlQuery[WithCursor[A]](query)
      recurse  <- response.cursor.fold(Future.successful(List(response.value))): cursor =>
                    executePagedGqlQuery[A](query.withVariable("cursor", JsString(cursor)), cursorPath)
                      .map(response.value :: _)
    yield recurse

  private case class WithCursor[A](
    cursor: Option[String],
    value : A
  )

  private def withCounter[T](name: String)(f: Future[T]) =
    f.andThen:
      case Success(_) =>
        metricRegistry.counter(s"$name.success").inc()
      case Failure(_) =>
        metricRegistry.counter(s"$name.failure").inc()

object GithubConnector:

  case class GraphqlQuery(
    query    : String,
    variables: Map[String, JsValue] = Map.empty
  ):

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

  private val repositoryFields =
    """
      name
      url
      isFork
      createdAt
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
          requiredStatusCheckContexts
        }
      }
      lastFiveCommits: defaultBranchRef {
        target {
          ... on Commit {
            history(first: 5) {
              nodes {
                author {
                  email
                }
                committedDate
              }
            }
          }
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
      hasPomXml: object(expression: "HEAD:pom.xml") {
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
                nodes {
                  $repositoryFields
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

  val getOpenPrsQuery: GraphqlQuery =
    GraphqlQuery(
      """
        query($cursor: String) {
          organization(login: "hmrc") {
            repositories(first: 100, after: $cursor, isArchived:false, orderBy: {field: CREATED_AT, direction: ASC}) {
              pageInfo {
                endCursor
              }
              nodes {
                name
                pullRequests(states: OPEN, first: 100) {
                  nodes {
                    title
                    url
                    author {
                      login
                    }
                    createdAt
                  }
                }
              }
            }
          }
        }
      """
    )

  val getOpenPrsForRepoQuery: GraphqlQuery =
    GraphqlQuery(
      """
        query($repoName: String!, $cursor: String) {
          organization(login: "hmrc") {
            repository(name: $repoName) {
              pullRequests(states: OPEN, first: 100, after: $cursor) {
                pageInfo {
                  endCursor
                }
                nodes {
                  title
                  url
                  author {
                    login
                  }
                  createdAt
                }
              }
            }
          }
        }
      """
    )

case class GhTeam(
  name     : String,
  createdAt: Instant
):
  def githubSlug: String =
    name.replaceAll(" - | |\\.", "-").toLowerCase

object GhTeam:

  val reads: Reads[GhTeam] =
    ( (__ \ "name"     ).read[String]
    ~ (__ \ "createdAt").read[Instant]
    )(apply _)

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
):

  def toGitRepository: GitRepository =
    val manifestDetails: ManifestDetails =
      repositoryYamlText
        .flatMap(ManifestDetails.parse(name, _))
        .getOrElse(
          ManifestDetails(
            organisation         = None,
            repoType             = None,
            serviceType          = None,
            testType             = None,
            tags                 = None,
            digitalServiceName   = None,
            description          = None,
            endOfLifeDate        = None,
            owningTeams          = Seq.empty,
            prototypeName        = None,
            prototypeAutoPublish = None
          )
        )

    val repoType: RepoType =
      if   manifestDetails.organisation == Some(Organisation.Mdtp)
      then manifestDetails.repoType.getOrElse(repoTypeHeuristics.inferredRepoType)
      else RepoType.Other

    val testType: Option[TestType] =
      manifestDetails
        .testType
        .orElse(ManifestDetails.deriveTestType(name))

    val prototypeName: Option[String] =
      Option.when(repoType == RepoType.Prototype)(
        manifestDetails
          .prototypeName
          .getOrElse(name) // default to repository name if not defined in repository.yaml
      )

    GitRepository(
      name                 = name,
      organisation         = manifestDetails.organisation,
      description          = manifestDetails.description.getOrElse(""),
      url                  = htmlUrl,
      createdDate          = createdDate,
      lastActiveDate       = pushedAt,
      endOfLifeDate        = manifestDetails.endOfLifeDate,
      isPrivate            = isPrivate,
      repoType             = repoType,
      serviceType          = manifestDetails.serviceType,
      testType             = testType,
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

object GhRepository:

  case class ManifestDetails(
    organisation        : Option[Organisation],
    repoType            : Option[RepoType],
    serviceType         : Option[ServiceType],
    testType            : Option[TestType],
    tags                : Option[Set[Tag]],
    digitalServiceName  : Option[String],
    description         : Option[String],
    endOfLifeDate       : Option[Instant],
    owningTeams         : Seq[String],
    isDeprecated        : Boolean = false,
    prototypeName       : Option[String],
    prototypeAutoPublish: Option[Boolean]
  )

  object ManifestDetails:
    import uk.gov.hmrc.teamsandrepositories.util.YamlMap

    private val logger = Logger(this.getClass)

    private def formatDigitalServiceName(name: String): String =
      name
        .split(" ").map(_.capitalize).mkString(" ")
        .split("-").map(_.capitalize).mkString("-")
        .trim

    def deriveTestType(repoName: String): Option[TestType] =
      repoName match
        case name if "(?i)(performance|perf)(-test(s)?)".r.findFirstIn(name).isDefined          => Some(TestType.Performance)
        case name if "(?i)(acceptance|ui|journey|api)(-test(s)?)".r.findFirstIn(name).isDefined => Some(TestType.Acceptance)
        case name if "(?i)(contract-test(s)?)".r.findFirstIn(name).isDefined                    => Some(TestType.Contract)
        case _ => None

    def parse(repoName: String, repositoryYaml: String): Option[ManifestDetails] =
      YamlMap.parse(repositoryYaml) match
        case Failure(exception) =>
          logger.warn(s"repository.yaml for $repoName is not valid YAML and could not be parsed. Parsing Exception: ${exception.getMessage}")
          None
        case Success(config) =>
          val manifestDetails = ManifestDetails(
            organisation         = Parser[Organisation].parse(config.get[String]("organisation").getOrElse(Organisation.Mdtp.asString)).toOption
          , repoType             = Parser[RepoType    ].parse(config.get[String]("type"        ).getOrElse("")).toOption
          , serviceType          = Parser[ServiceType ].parse(config.get[String]("service-type").getOrElse("")).toOption
          , testType             = Parser[TestType].parse(config.get[String]("test-type").getOrElse("")).toOption
          , tags                 = config.getArray("tags").map(_.flatMap(str => Parser[Tag].parse(str).toOption).toSet)
          , digitalServiceName   = config.get[String]("digital-service").map(formatDigitalServiceName)
          , description          = config.get[String]("description")
          , endOfLifeDate        = config.getAsOpt[Date]("end-of-life-date").map(_.toInstant)
          , owningTeams          = config.getArray("owning-teams").getOrElse(Nil)
          , isDeprecated         = config.get[Boolean]("deprecated").getOrElse(false)
          , prototypeName        = config.get[String]("prototype-name")
          , prototypeAutoPublish = config.get[Boolean]("prototype-auto-publish")
          )
          Some(manifestDetails)

  case class RepoTypeHeuristics(
    prototypeInName    : Boolean,
    testsInName        : Boolean,
    hasApplicationConf : Boolean,
    hasDeployProperties: Boolean,
    hasProcfile        : Boolean,
    hasSrcMainScala    : Boolean,
    hasSrcMainJava     : Boolean,
    hasPomXml          : Boolean,
    hasTags            : Boolean
  ):

    def inferredRepoType: RepoType =
      if prototypeInName then
        RepoType.Prototype
      else if testsInName then
        RepoType.Test
      else if hasApplicationConf || hasDeployProperties || hasProcfile then
        RepoType.Service
      else if (hasSrcMainScala || hasSrcMainJava) && hasTags then
        RepoType.Library
      else
        RepoType.Other

  object RepoTypeHeuristics:
    val reads: Reads[RepoTypeHeuristics] =
      ( (__ \ "name"                      ).read[String].map(_.endsWith("-prototype"))
      ~ (__ \ "name"                      ).read[String].map(name => name.endsWith("-tests") || name.endsWith("-test"))
      ~ (__ \ "hasApplicationConf" \ "id" ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasDeployProperties" \ "id").readNullable[String].map(_.isDefined)
      ~ (__ \ "hasProcfile" \ "id"        ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasSrcMainScala" \ "id"    ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasSrcMainJava" \ "id"     ).readNullable[String].map(_.isDefined)
      ~ (__ \ "hasPomXml" \ "id"          ).readNullable[String].map(_.isDefined)
      ~ (__ \ "tags" \ "totalCount"       ).readNullable[Int].map(_.exists(_ > 0))
      )(apply _)

  def reads(gitHubConfig: GithubConfig): Reads[GhRepository] =
    ( (__ \ "name"                                            ).read[String]
    ~ (__ \ "url"                                             ).read[String]
    ~ (__ \ "isFork"                                          ).read[Boolean]
    ~ (__ \ "createdAt"                                       ).read[Instant]
    ~ (__ \ "lastFiveCommits" \ "target" \ "history" \ "nodes").read[List[JsObject]].map { commits =>
      commits
        .filterNot: commit =>
          val authorEmail = (commit \ "author" \ "email").asOpt[String]
          authorEmail.exists(gitHubConfig.excludedUsers.contains)
        .map(commit => (commit \ "committedDate").as[Instant])
        .headOption.getOrElse(Instant.MIN)
      }.orElse(Reads.pure(Instant.MIN))
    ~ (__ \ "isPrivate"                                       ).read[Boolean]
    ~ (__ \ "primaryLanguage" \ "name"                        ).readNullable[String]
    ~ (__ \ "isArchived"                                      ).read[Boolean]
    ~ (__ \ "defaultBranchRef" \ "name"                       ).readWithDefault("main")
    ~ (__ \ "defaultBranchRef" \ "branchProtectionRule"       ).readNullable(BranchProtection.format)
    ~ (__ \ "repositoryYaml" \ "text"                         ).readNullable[String]
    ~ RepoTypeHeuristics.reads
    )(apply _)

case class RateLimitMetrics(
  limit    : Int,
  remaining: Int,
  reset    : Int
)

object RateLimitMetrics:

  enum Resource(val asString: String):
    case Core    extends Resource("core"   )
    case GraphQl extends Resource("graphql")

  def reads(resource: Resource): Reads[RateLimitMetrics] =
    Reads.at(__ \ "resources" \ resource.asString)(
      ( (__ \ "limit"    ).read[Int]
      ~ (__ \ "remaining").read[Int]
      ~ (__ \ "reset"    ).read[Int]
      )(RateLimitMetrics.apply _)
    )

case class ApiRateLimitExceededException(
  exception: Throwable
) extends RuntimeException(exception)

case class ApiAbuseDetectedException(
  exception: Throwable
) extends RuntimeException(exception)

object RateLimit:
  val logger: Logger = Logger(getClass)

  def convertRateLimitErrors[A]: PartialFunction[Throwable, Future[A]] =
    case e if e.getMessage.toLowerCase.contains("api rate limit exceeded")
           || e.getMessage.toLowerCase.contains("have exceeded a secondary rate limit") =>
      logger.error("=== Api rate limit has been reached ===", e)
      Future.failed(ApiRateLimitExceededException(e))

    case e if e.getMessage.toLowerCase.contains("triggered an abuse detection mechanism") =>
      logger.error("=== Api abuse detected ===", e)
      Future.failed(ApiAbuseDetectedException(e))

case class BranchProtection(
  requiresApprovingReviews: Boolean,
  dismissesStaleReview    : Boolean,
  requiresCommitSignatures: Boolean,
  requiredStatusChecks    : Seq[String] = Seq.empty
)

object BranchProtection:

  val format: Format[BranchProtection] =
    ( (__ \ "requiresApprovingReviews"   ).format[Boolean]
    ~ (__ \ "dismissesStaleReviews"      ).format[Boolean]
    ~ (__ \ "requiresCommitSignatures"   ).format[Boolean]
    ~ (__ \ "requiredStatusCheckContexts").formatWithDefault[Seq[String]](Seq.empty)
    )(apply, b => Tuple.fromProductTyped(b))

case class DismissalRestrictions(
  users: List[String],
  teams: List[String]
)

object DismissalRestrictions:
  val reads: Reads[DismissalRestrictions] =
    ( (__ \ "users").read[List[JsValue]].map(_.map(js => (js \ "login").as[String]))
    ~ (__ \ "teams").read[List[JsValue]].map(_.map(js => (js \ "slug" ).as[String]))
    )(apply)

  val writes: Writes[DismissalRestrictions] =
    ( (__ \ "users").write[List[String]]
    ~ (__ \ "teams").write[List[String]]
    )(dr => Tuple.fromProductTyped(dr))

case class RequiredPullRequestReviews(
  dismissStaleReviews         : Boolean,
  requireCodeOwnerReviews     : Boolean,
  requireLastPushApproval     : Boolean,
  requiredApprovingReviewCount: Int,
  dismissalRestrictions       : Option[DismissalRestrictions]
)

object RequiredPullRequestReviews:
  given Reads[DismissalRestrictions]  = DismissalRestrictions.reads
  given Writes[DismissalRestrictions] = DismissalRestrictions.writes
  val format: Format[RequiredPullRequestReviews] =
    ( (__ \ "dismiss_stale_reviews"          ).format[Boolean]
    ~ (__ \ "require_code_owner_reviews"     ).format[Boolean]
    ~ (__ \ "require_last_push_approval"     ).format[Boolean]
    ~ (__ \ "required_approving_review_count").format[Int]
    ~ (__ \ "dismissal_restrictions"         ).formatNullable[DismissalRestrictions]
    )(apply, r => Tuple.fromProductTyped(r))

case class RequiredStatusChecks(
  strict  : Boolean,
  contexts: List[String]
)

object RequiredStatusChecks:
  val format: Format[RequiredStatusChecks] =
    ( (__ \ "strict"  ).format[Boolean]
    ~ (__ \ "contexts").format[List[String]]
    )(apply, r => Tuple.fromProductTyped(r))

case class BranchProtectionRules(
  requiredPullRequestReviews    : Option[RequiredPullRequestReviews],
  requiredStatusChecks          : Option[RequiredStatusChecks],
  requiredSignatures            : Boolean,
  enforceAdmins                 : Boolean,
  requiredLinearHistory         : Boolean,
  allowForcePushes              : Boolean,
  allowDeletions                : Boolean,
  blockCreations                : Boolean,
  requiredConversationResolution: Boolean,
  lockBranch                    : Boolean,
  allowForkSyncing              : Boolean,
  restrictions                  : JsValue
)

object BranchProtectionRules:
  given Format[RequiredPullRequestReviews] = RequiredPullRequestReviews.format
  given Format[RequiredStatusChecks]       = RequiredStatusChecks.format
  val reads: Reads[BranchProtectionRules] =
    ( (__ \ "required_pull_request_reviews"               ).readNullable[RequiredPullRequestReviews]
    ~ (__ \ "required_status_checks"                      ).readNullable[RequiredStatusChecks]
    ~ (__ \ "required_signatures"              \ "enabled").read[Boolean]
    ~ (__ \ "enforce_admins"                   \ "enabled").read[Boolean]
    ~ (__ \ "required_linear_history"          \ "enabled").read[Boolean]
    ~ (__ \ "allow_force_pushes"               \ "enabled").read[Boolean]
    ~ (__ \ "allow_deletions"                  \ "enabled").read[Boolean]
    ~ (__ \ "block_creations"                  \ "enabled").read[Boolean]
    ~ (__ \ "required_conversation_resolution" \ "enabled").read[Boolean]
    ~ (__ \ "lock_branch"                      \ "enabled").read[Boolean]
    ~ (__ \ "allow_fork_syncing"               \ "enabled").read[Boolean]
    ~ (__ \ "restrictions"                                ).readWithDefault[JsValue](JsNull)
    )(apply)

  val writes: Writes[BranchProtectionRules] =
    ( (__ \ "required_pull_request_reviews"   ).writeNullable[RequiredPullRequestReviews]
    ~ (__ \ "required_status_checks"          ).writeNullable[RequiredStatusChecks]
    ~ (__ \ "required_signatures"             ).write[Boolean]
    ~ (__ \ "enforce_admins"                  ).write[Boolean]
    ~ (__ \ "required_linear_history"         ).write[Boolean]
    ~ (__ \ "allow_force_pushes"              ).write[Boolean]
    ~ (__ \ "allow_deletions"                 ).write[Boolean]
    ~ (__ \ "block_creations"                 ).write[Boolean]
    ~ (__ \ "required_conversation_resolution").write[Boolean]
    ~ (__ \ "lock_branch"                     ).write[Boolean]
    ~ (__ \ "allow_fork_syncing"              ).write[Boolean]
    ~ (__ \ "restrictions"                    ).write[JsValue]
    )(b => Tuple.fromProductTyped(b))
