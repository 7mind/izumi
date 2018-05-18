scalaVersion := "2.12.6"

// https://github.com/coursier/coursier#sbt-plugin
val coursier = "1.1.0-M3"
addSbtPlugin("io.get-coursier" % "sbt-coursier" % coursier)
addSbtPlugin("io.get-coursier" % "sbt-shading" % coursier)

// https://github.com/sbt/sbt-assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

// bootstrap
lazy val izumiDepsPlugin = RootProject(file("../sbt/sbt-izumi-deps"))
lazy val izumiPlugin = RootProject(file("../sbt/sbt-izumi"))
lazy val root = project.in(file(".")).dependsOn(izumiDepsPlugin, izumiPlugin)

//
libraryDependencies += {
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
}


