import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

object MicroServiceBuild extends Build with MicroService {

  override val appName = "teams-and-repositories"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.core.PlayVersion.current

  private val microserviceBootstrapVersion = "6.8.0"
  private val playUrlBindersVersion = "2.1.0"
  private val domainVersion = "4.1.0"
  private val hmrcTestVersion = "2.3.0"
  private val githubClientVersion = "1.16.0-1-g5f6fb3f"
  private val playReactivemongoVersion = "6.0.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-25" % "0.7.0",
    "uk.gov.hmrc" %% "github-client" % githubClientVersion,
    "uk.gov.hmrc" %% "play-url-binders" % playUrlBindersVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "org.yaml" % "snakeyaml" % "1.17",
    "org.apache.httpcomponents" % "httpcore" % "4.3.2",
    "org.apache.httpcomponents" % "httpclient" % "4.3.5",
    "uk.gov.hmrc" %% "mongo-lock" % "5.0.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0" % scope,
        "org.pegdown" % "pegdown" % "1.4.2" % scope,
        "com.typesafe.play" %% "play-test" % current % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope,
        "com.github.tomakehurst" % "wiremock" % "1.52" % scope,
        "org.mockito" % "mockito-core" % "2.3.5" % scope
      )
    }.test
  }


  def apply() = compile ++ Test()
}

