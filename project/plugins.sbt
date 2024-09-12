// DO NOT EDIT THIS FILE
// IT IS AUTOGENERATED BY `sbtgen.sc` SCRIPT
// ALL CHANGES WILL BE LOST

////////////////////////////////////////////////////////////////////////////////

addSbtPlugin("io.7mind.izumi.sbt" % "sbt-izumi" % "0.0.104")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % PV.sbt_assembly)

addSbtPlugin("com.jsuereth" % "sbt-pgp" % PV.sbt_pgp)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % PV.sbt_scoverage)

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % PV.sbt_unidoc)

addSbtPlugin("com.github.sbt" % "sbt-site" % PV.sbt_site)

addSbtPlugin("com.github.sbt" % "sbt-site-paradox" % PV.sbt_site)

addSbtPlugin("com.github.sbt" % "sbt-ghpages" % PV.sbt_ghpages)

addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % PV.sbt_paradox)

addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-theme" % PV.sbt_paradox)

addSbtPlugin("com.github.sbt" % "sbt-paradox-material-theme" % PV.sbt_paradox_material_theme)

addSbtPlugin("org.scalameta" % "sbt-mdoc" % PV.sbt_mdoc)

// Ignore scala-xml version conflict between scoverage where `coursier` requires scala-xml v2
// and scoverage requires scala-xml v1 on Scala 2.12,
// introduced when updating scoverage from 1.9.3 to 2.0.5
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
