import sbt._

object AppDependencies {

  import play.core.PlayVersion.current

  val compile = Seq(
    "uk.gov.hmrc"               %% "bootstrap-play-25"  % "1.7.0",
    "uk.gov.hmrc"               %% "github-client"      % "1.18.0",
    "org.yaml"                  % "snakeyaml"           % "1.17",
    "org.apache.httpcomponents" % "httpcore"            % "4.3.2",
    "org.apache.httpcomponents" % "httpclient"          % "4.3.5",
    "uk.gov.hmrc"               %% "mongo-lock"         % "5.1.0",
    "uk.gov.hmrc"               %% "play-reactivemongo" % "6.2.0"
  )
  val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "2.3.0" % "test",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0" % "test",
    "org.pegdown"            % "pegdown"             % "1.4.2" % "test",
    "com.typesafe.play"      %% "play-test"          % current % "test",
    "uk.gov.hmrc"            %% "reactivemongo-test" % "2.0.0" % "test",
    "com.github.tomakehurst" % "wiremock"            % "1.52"  % "test",
    "org.mockito"            % "mockito-core"        % "2.3.5" % "test"
  )
}
