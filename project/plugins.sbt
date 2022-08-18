// https://www.scala-js.org/
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.10.1")

// https://github.com/portable-scala/sbt-crossproject
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")

// https://scalacenter.github.io/scalajs-bundler/
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.20.0")

// https://github.com/scala-js/jsdependencies
addSbtPlugin("org.scala-js" % "sbt-jsdependencies" % "1.0.2")

////////////////////////////////////////////////////////////////////////////////

addSbtPlugin("io.7mind.izumi.sbt" % "sbt-izumi" % "0.0.95")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % PV.sbt_assembly)

addSbtPlugin("com.jsuereth" % "sbt-pgp" % PV.sbt_pgp)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % PV.sbt_scoverage)

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % PV.sbt_unidoc)

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % PV.sbt_site)

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % PV.sbt_ghpages)

addSbtPlugin("io.github.jonas" % "sbt-paradox-material-theme" % PV.sbt_paradox_material_theme)

addSbtPlugin("org.scalameta" % "sbt-mdoc" % PV.sbt_mdoc)

