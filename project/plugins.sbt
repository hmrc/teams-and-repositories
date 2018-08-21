resolvers += Resolver.url("hmrc-sbt-plugin-releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)

resolvers += "HMRC Releases" at "https://dl.bintray.com/hmrc/releases"

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.13.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.9.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.13.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "1.1.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.12")
