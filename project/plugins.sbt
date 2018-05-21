scalaVersion := "2.12.6"

// https://github.com/coursier/coursier#sbt-plugin
val coursier = "1.1.0-M3"
addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursier)
addSbtPlugin("io.get-coursier" % "sbt-shading" % coursier)

// https://github.com/sbt/sbt-assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

// https://www.scala-sbt.org/sbt-site/
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.2")

// https://github.com/sbt/sbt-ghpages
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.2")

// https://github.com/sbt/sbt-unidoc
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")

// https://jonas.github.io/paradox-material-theme/
addSbtPlugin("io.github.jonas" % "sbt-paradox-material-theme" % "0.4.0")

// bootstrap
lazy val izumiDepsPlugin = RootProject(file("../sbt/sbt-izumi-deps"))
lazy val izumiPlugin = RootProject(file("../sbt/sbt-izumi"))
lazy val root = project.in(file(".")).dependsOn(izumiDepsPlugin, izumiPlugin)

//
libraryDependencies += {
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
}


