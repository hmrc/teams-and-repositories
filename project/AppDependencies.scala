import sbt._

object AppDependencies {

  import play.core.PlayVersion.current
  import play.sbt.PlayImport._

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-27"  % "2.6.0",
    "uk.gov.hmrc"            %% "github-client"              % "2.14.0",
    "org.yaml"               % "snakeyaml"                   % "1.17",
    "uk.gov.hmrc"            %% "mongo-lock"                 % "6.23.0-play-27",
    "uk.gov.hmrc"            %% "metrix"                     % "4.7.0-play-27",
    "uk.gov.hmrc"            %% "simple-reactivemongo"       % "7.30.0-play-27",
    "org.typelevel"          %% "cats-core"                  % "2.0.0"
  )
  val test = Seq(
    "com.typesafe.play"      %% "play-test"                % current          % Test,
    "org.scalatest"          %% "scalatest"                % "3.1.1"          % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"       % "4.0.3"          % Test,
    "org.scalatestplus"      %% "scalatestplus-mockito"    % "1.0.0-M2"       % Test,
    "org.scalatest"          %% "scalatest-mustmatchers"   % "3.2.0-M4"       % Test,
    "org.mockito"            %% "mockito-scala"            % "1.5.11"         % Test,
    "org.mockito"            %% "mockito-scala-scalatest"  % "1.5.11"         % Test,
    "com.vladsch.flexmark"   %  "flexmark-all"             % "0.35.10"        % Test, // replaces pegdown for newer scalatest
    "com.github.tomakehurst" % "wiremock"                  % "1.58"           % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test"       % "4.21.0-play-27" % Test
  )
}