import sbt._
import play.core.PlayVersion

object AppDependencies {

  import play.sbt.PlayImport._
  val hmrcMongoVersion = "0.36.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-27"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-27" % "2.24.0",
    "uk.gov.hmrc"       %% "github-client"             % "2.15.0",
    "org.yaml"          % "snakeyaml"                  % "1.17",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-metrix-play-27" % hmrcMongoVersion,
    "org.typelevel"     %% "cats-core"                 % "2.0.0"
  )
  val test = Seq(
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-27" % hmrcMongoVersion    % Test,
    "com.typesafe.play"      %% "play-test"               % PlayVersion.current % Test,
    "org.scalatest"          %% "scalatest"               % "3.1.1"             % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"      % "4.0.3"             % Test,
    "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2"          % Test,
    "org.scalatest"          %% "scalatest-mustmatchers"  % "3.2.0-M4"          % Test,
    "org.mockito"            %% "mockito-scala"           % "1.5.11"            % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.5.11"            % Test,
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.35.10"           % Test, // replaces pegdown for newer scalatest
    "com.github.tomakehurst" % "wiremock"                 % "1.58"              % Test
  )
}
