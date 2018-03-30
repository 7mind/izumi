scalaVersion := "2.12.4"

// https://github.com/coursier/coursier#sbt-plugin
val coursier = "1.0.3"
addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursier)
addSbtPlugin("io.get-coursier" % "sbt-shading" % coursier)

// bootstrap
lazy val izumiPlugin = RootProject(file("../sbt/sbt-izumi"))
lazy val root = project.in(file(".")).dependsOn(izumiPlugin)

//
libraryDependencies += {
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
}
