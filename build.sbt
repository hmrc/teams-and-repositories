import play.sbt.routes.RoutesKeys

lazy val microservice = Project("teams-and-repositories", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(majorVersion := 11)
  .settings(PlayKeys.playDefaultPort := 9015)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalaVersion := "3.3.3")
  // Disabled until implemented in a later Scala version
//  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.teamsandrepositories.models.RepoType",
      "uk.gov.hmrc.teamsandrepositories.models.ServiceType",
      "uk.gov.hmrc.teamsandrepositories.models.Tag"
    ),
  )
