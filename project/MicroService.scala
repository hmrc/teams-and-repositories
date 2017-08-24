import play.sbt.PlayScala
import play.sbt.routes.RoutesKeys.{routesGenerator, _}
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._


trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._

  val appName: String

  lazy val appDependencies: Seq[ModuleID] = ???
  lazy val plugins: Seq[Plugins] = Seq()
  lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(PlayScala) ++ plugins: _*)
    .settings(playSettings: _*)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      scalaVersion := "2.11.11",
      libraryDependencies ++= appDependencies,
      parallelExecution in Test := false,
      fork in Test := false,
      targetJvm := "jvm-1.8",
      retrieveManaged := true,
      routesGenerator := StaticRoutesGenerator
    )
    .disablePlugins(sbt.plugins.JUnitXmlReportPlugin)
    .settings(resolvers ++= Seq(Resolver.bintrayRepo("hmrc", "releases"), Resolver.jcenterRepo))
}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}
