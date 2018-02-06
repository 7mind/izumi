scalaVersion := "2.12.4"

// https://github.com/coursier/coursier#sbt-plugin
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.1")

// bootstrap
lazy val izumiPlugin = RootProject(file("../sbt-izumi"))
lazy val root = project.in( file(".") ).dependsOn( izumiPlugin )

// 
libraryDependencies += { "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value }
