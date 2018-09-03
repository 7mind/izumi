// we need this to be copied here for build bootstrapping
libraryDependencies ++= Seq(
  "org.scala-sbt" % "sbt" % sbtVersion.value
)

sbtPlugin := true

// https://github.com/coursier/coursier#sbt-plugin
val coursier = "1.1.0-M7"
addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursier)
addSbtPlugin("io.get-coursier" % "sbt-shading" % coursier)

// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

// http://www.scala-sbt.org/sbt-pgp/
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2")

// https://github.com/sbt/sbt-git
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")

// http://www.scalastyle.org/sbt.html
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// https://github.com/orrsella/sbt-stats
addSbtPlugin("com.orrsella" % "sbt-stats" % "1.0.7")

// https://github.com/xerial/sbt-sonatype
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")

// https://github.com/sbt/sbt-release
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")

// https://github.com/jrudolph/sbt-dependency-graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

// https://github.com/rtimush/sbt-updates
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")

// https://github.com/laughedelic/sbt-publish-more // TODO: signed publishers: https://github.com/laughedelic/sbt-publish-more/issues/7
addSbtPlugin("laughedelic" % "sbt-publish-more" % "0.1.0")

//// https://github.com/sbt/sbt-duplicates-finder
//addSbtPlugin("org.scala-sbt" % "sbt-duplicates-finder" % "0.8.1")

// https://github.com/sbt/sbt-bintray
//addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.1")

// https://github.com/sksamuel/sbt-scapegoat
//addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.7")

// https://github.com/xerial/sbt-pack // failing on sbt 1.1.0
//addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.9.1")
