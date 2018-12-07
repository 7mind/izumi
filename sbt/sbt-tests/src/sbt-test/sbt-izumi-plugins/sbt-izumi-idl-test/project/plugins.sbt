lazy val pluginVersion = if (sys.props.isDefinedAt("plugin.version")) {
  sys.props("plugin.version")
} else {
  IO.read(new File("../../../../../../version.sbt")).split("\"")(1)
}

updateOptions := updateOptions.value.withLatestSnapshots(false)

addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi-deps" % pluginVersion)
addSbtPlugin("com.github.pshirshov.izumi.r2" %% "sbt-idealingua" % pluginVersion)
addSbtPlugin("com.github.pshirshov.izumi.r2" %% "sbt-izumi" % pluginVersion)
addSbtPlugin("com.github.pshirshov.izumi.r2" %% "sbt-izumi" % pluginVersion)
