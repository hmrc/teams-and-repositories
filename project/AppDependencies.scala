import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {
  val bootstrapPlayVersion = "9.13.0"
  val hmrcMongoVersion     = "2.6.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30"    % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-metrix-play-30"    % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "internal-auth-client-play-30" % "4.0.0",
    "org.yaml"               %  "snakeyaml"                    % "2.3",
    "org.typelevel"          %% "cats-core"                    % "2.13.0",
    "software.amazon.awssdk" %  "sqs"                          % "2.31.43"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapPlayVersion % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % hmrcMongoVersion     % Test
  )
}
