import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport.ws

object AppDependencies {
  val bootstrapPlayVersion = "6.3.0"
  val hmrcMongoVersion     = "0.70.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-28"    % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-metrix-play-28"    % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "internal-auth-client-play-28" % "1.2.0",
    "org.yaml"               %  "snakeyaml"                    % "1.28",
    "org.typelevel"          %% "cats-core"                    % "2.6.1",
    "org.codehaus.groovy"    %  "groovy-astbuilder"            % "3.0.12",
    "software.amazon.awssdk" %  "auth"                         % "2.18.21"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapPlayVersion % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % hmrcMongoVersion     % Test,
    "org.mockito"            %% "mockito-scala-scalatest" % "1.16.23"            % Test
  )
}
