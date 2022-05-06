import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport.ws

object AppDependencies {
  val bootstrapPlayVersion = "5.18.0"
  val hmrcMongoVersion     = "0.58.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"    % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"           % hmrcMongoVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-metrix-play-28"    % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "internal-auth-client-play-28" % "1.1.0",
    "org.yaml"          %  "snakeyaml"                    % "1.28",
    "org.typelevel"     %% "cats-core"                    % "2.6.1",
    "io.ticofab"        %% "aws-request-signer"           % "0.5.2"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapPlayVersion % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion     % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.16.23"            % Test
  )
}
