libraryDependencies ++= Seq(
  "org.scala-sbt" % "sbt" % sbtVersion.value
)

sbtPlugin := true

// https://github.com/xerial/sbt-pack
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.9.1")

// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

// http://www.scala-sbt.org/sbt-pgp/
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")

// https://github.com/sbt/sbt-git
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")

// http://www.scalastyle.org/sbt.html
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

// https://github.com/orrsella/sbt-stats
addSbtPlugin("com.orrsella" % "sbt-stats" % "1.0.7")

// https://github.com/laughedelic/sbt-publish-more
addSbtPlugin("laughedelic" % "sbt-publish-more" % "0.1.0")

// https://github.com/xerial/sbt-sonatype
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")

// https://github.com/sksamuel/sbt-scapegoat
//addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.7")

// https://github.com/coursier/coursier#sbt-plugin
val withCoursier = if (sys.props.get("build.coursier").contains("true")) {
  addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC13")
}
