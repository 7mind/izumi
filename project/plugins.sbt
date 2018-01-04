scalaVersion := "2.12.4"

// https://github.com/coursier/coursier#sbt-plugin
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0")

lazy val root = project.in( file(".") ).dependsOn( izumiPlugin )
lazy val izumiPlugin = RootProject(file("../sbt-izumi"))

libraryDependencies += { "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value }
