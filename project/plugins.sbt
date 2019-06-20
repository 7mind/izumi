scalaVersion := "2.12.8"

// bootstrap
lazy val izumiDepsPlugin = RootProject(file("../sbt/sbt-izumi-deps"))
lazy val izumiPlugin = RootProject(file("../sbt/sbt-izumi"))
lazy val root = project.in(file(".")).dependsOn(izumiDepsPlugin, izumiPlugin)

//
libraryDependencies += {
  "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value
}

// https://github.com/sbt/sbt-assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")

// https://github.com/sbt/sbt-unidoc
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")

// https://www.scala-sbt.org/sbt-site/
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")

// https://github.com/sbt/sbt-ghpages
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.3")

// https://jonas.github.io/paradox-material-theme/
addSbtPlugin("io.github.jonas" % "sbt-paradox-material-theme" % "0.6.0")

// https://github.com/scalameta/mdoc
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "1.3.1" )

// https://www.scala-js.org/
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.28")

// https://scalacenter.github.io/scalajs-bundler/
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.14.0")

// https://github.com/portable-scala/sbt-crossproject
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.6.0")



