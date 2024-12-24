import play.sbt.routes.RoutesKeys

lazy val microservice = Project("teams-and-repositories", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(majorVersion := 11)
  .settings(PlayKeys.playDefaultPort := 9015)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalaVersion := "3.3.4")
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(scalacOptions += "-Wconf:msg=Flag.*repeatedly:s")
  .settings(
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.teamsandrepositories.model.RepoType",
      "uk.gov.hmrc.teamsandrepositories.model.ServiceType",
      "uk.gov.hmrc.teamsandrepositories.model.Tag"
    ),
  )
