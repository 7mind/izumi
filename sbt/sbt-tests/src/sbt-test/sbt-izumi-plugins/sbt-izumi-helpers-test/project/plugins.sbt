lazy val pluginVersion = if (sys.props.isDefinedAt("plugin.version")) {
  sys.props("plugin.version")
} else {
  IO.read(new File("../../../../../../version.sbt")).split("\"")(1)
}

updateOptions := updateOptions.value.withLatestSnapshots(false)

addSbtPlugin("io.7mind.izumi" % "sbt-izumi-deps" % pluginVersion)
addSbtPlugin("io.7mind.izumi" %% "sbt-izumi" % pluginVersion)
