lazy val pluginVersion = if (sys.props.isDefinedAt("plugin.version")) {
  sys.props("plugin.version")
} else {
  IO.read(new File("../../../../../../version.sbt")).split("\"")(1)
}

addSbtPlugin("com.github.pshirshov.izumi.r2" %% "sbt-izumi" % pluginVersion)
