// https://www.scala-sbt.org/0.13/docs/Testing-sbt-plugins.html
libraryDependencies += { "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value }

addSbtPlugin("io.7mind.izumi.sbt" % "sbt-izumi-deps" % "0.0.10-SNAPSHOT" )
//////////////////////////////////////////////////////////////////////////////////

// http://www.scala-sbt.org/sbt-pgp/
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0-M2")

// https://github.com/scoverage/sbt-scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")

// https://github.com/sbt/sbt-unidoc
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.2")

// https://www.scala-sbt.org/sbt-site/
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.3")

// https://github.com/sbt/sbt-ghpages
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.6.3")

// https://jonas.github.io/paradox-material-theme/
addSbtPlugin("io.github.jonas" % "sbt-paradox-material-theme" % "0.6.0")

// https://github.com/scalameta/mdoc
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "1.3.2" )

