scalaVersion := "2.12.4"

// https://github.com/coursier/coursier#sbt-plugin
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0")

// bootstrap
lazy val izumiPlugin = RootProject(file("../sbt-izumi"))
lazy val root = project.in( file(".") ).dependsOn( izumiPlugin )

// 
libraryDependencies += { "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value }


// https://github.com/scoverage/sbt-coveralls
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.2")