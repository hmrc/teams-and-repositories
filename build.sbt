import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "teams-and-repositories"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .settings( majorVersion := 10 )
  .settings(publishingSettings: _*)
  .settings(PlayKeys.playDefaultPort := 9015)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)
  .settings(resolvers += Resolver.jcenterRepo)
