import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport.ws

object AppDependencies {
  val bootstrapPlayVersion = "5.7.0"
  val hmrcMongoVersion     = "0.51.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % "4.0.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-metrix-play-28" % hmrcMongoVersion,
    "org.yaml"          %  "snakeyaml"                 % "1.28",
    "org.typelevel"     %% "cats-core"                 % "2.6.1"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapPlayVersion % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion     % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.16.23"            % Test,
    "com.vladsch.flexmark"   %  "flexmark-all"            % "0.35.10"            % Test
  )
}
