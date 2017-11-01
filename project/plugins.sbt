scalaVersion := "2.12.4"

// https://github.com/coursier/coursier#sbt-plugin
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC13")

lazy val root = project.in( file(".") ).dependsOn( izumiPlugin )
lazy val izumiPlugin = RootProject(file("../sbt-izumi"))

