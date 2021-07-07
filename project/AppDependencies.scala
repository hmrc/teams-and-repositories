import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport.ws

object AppDependencies {
  val hmrcMongoVersion = "0.44.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % "4.0.0",
    "org.yaml"          %  "snakeyaml"                 % "1.28",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-metrix-play-28" % hmrcMongoVersion,
    "org.typelevel"     %% "cats-core"                 % "2.4.2"
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion    % Test,
    "com.typesafe.play"      %% "play-test"               % PlayVersion.current % Test,
    "org.scalatest"          %% "scalatest"               % "3.2.3"             % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"             % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.16.23"           % Test,
    "com.vladsch.flexmark"   %  "flexmark-all"            % "0.35.10"           % Test,
    "com.github.tomakehurst" %  "wiremock"                % "1.58"              % Test
  )
}
