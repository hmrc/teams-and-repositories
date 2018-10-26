import sbt._

object AppDependencies {

  import play.core.PlayVersion.current
  import play.sbt.PlayImport._

  val bootstrapPlayVersion = "0.23.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"               %% "bootstrap-play-26"  % bootstrapPlayVersion,
    "uk.gov.hmrc"               %% "github-client"      % "2.5.0",
    "org.yaml"                  % "snakeyaml"           % "1.17",
    "org.apache.httpcomponents" % "httpcore"            % "4.3.2",
    "org.apache.httpcomponents" % "httpclient"          % "4.3.5",
    "uk.gov.hmrc"               %% "mongo-lock"         % "6.1.0-play-26"
  )
  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"  % bootstrapPlayVersion % Test classifier "tests",
    "uk.gov.hmrc"            %% "hmrctest"           % "3.2.0"              % Test,
    "org.scalatest"          %% "scalatest"          % "3.0.4"              % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"              % Test,
    "org.pegdown"            % "pegdown"             % "1.5.0"              % Test,
    "com.typesafe.play"      %% "play-test"          % current              % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.1.0-play-26"      % Test,
    "com.github.tomakehurst" % "wiremock"            % "1.52"               % Test,
    "org.mockito"            % "mockito-core"        % "2.3.5"              % Test
  )
}
