// we need this to be copied here for build bootstrapping
libraryDependencies ++= Seq(
  "org.scala-sbt" % "sbt" % sbtVersion.value
)

sbtPlugin := true

// https://github.com/coursier/coursier#sbt-plugin
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M8")

// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")

// http://www.scala-sbt.org/sbt-pgp/
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0-M2")

// https://github.com/sbt/sbt-git
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")

// http://www.scalastyle.org/sbt.html
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// https://github.com/orrsella/sbt-stats
addSbtPlugin("com.orrsella" % "sbt-stats" % "1.0.7")

// https://github.com/xerial/sbt-sonatype
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.6")

// https://github.com/sbt/sbt-release
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")

// https://github.com/jrudolph/sbt-dependency-graph
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

// https://github.com/laughedelic/sbt-publish-more // TODO: signed publishers: https://github.com/laughedelic/sbt-publish-more/issues/7
addSbtPlugin("laughedelic" % "sbt-publish-more" % "0.1.0")

// https://github.com/portable-scala/sbt-crossproject
addSbtPlugin("org.portable-scala" % "sbt-crossproject" % "0.6.1")

// https://github.com/sbt/sbt-duplicates-finder
addSbtPlugin("org.scala-sbt" % "sbt-duplicates-finder" % "0.8.1")

////https://github.com/aiyanbo/sbt-dependency-updates
//addSbtPlugin("org.jmotor.sbt" % "sbt-dependency-updates" % "1.2.0")
