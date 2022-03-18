import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val silencerVersion = "1.7.5"

lazy val microservice = Project("teams-and-repositories", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(majorVersion := 11)
  .settings(SbtDistributablesPlugin.publishingSettings: _*)
  .settings(PlayKeys.playDefaultPort := 9015)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalaVersion := "2.12.14")
  .settings(
    // Use the silencer plugin to suppress warnings from unused imports in routes etc.
    scalacOptions += "-P:silencer:pathFilters=routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
  )
  .settings(
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.teamsandrepositories.models.RepoType"
    ),
  )
