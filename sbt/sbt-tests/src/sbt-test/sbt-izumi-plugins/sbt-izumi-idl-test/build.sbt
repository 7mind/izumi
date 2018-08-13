import sbt.Keys._

import IzumiConvenienceTasksPlugin.Keys._
enablePlugins(IzumiEnvironmentPlugin)

lazy val pluginVersion = if (sys.props.isDefinedAt("plugin.version")) {
  sys.props("plugin.version")
} else {
  IO.read(new File("../../../../../../version.sbt")).split("\"")(1)
}

// -- build settings, root artifact settings, etc
name := "sbt-izumi-idl-test"
crossScalaVersions in ThisBuild := Seq(
  IzumiRootDeps.V.scala_212
)

// unfortunately we have to use this bcs conditional settings in plugins don't work
defaultStubPackage in ThisBuild := Some("org.test.project")
organization in ThisBuild := "com.github.pshirshov.izumi.test.idl"


// -- settings groups identifiers
val GlobalSettings = new DefaultGlobalSettingsGroup {
  // these settings will be added into each project handled by Izumi
  override val settings: Seq[sbt.Setting[_]] = Seq()

  // these dependencies will be added into each project handled by Izumi
  override val sharedDeps = Set(
    Izumi.R.idealingua_model
    , Izumi.R.idealingua_runtime_rpc_http4s
    , Izumi.R.idealingua_runtime_rpc_scala
  )
}


// -- common project directories
val inRoot = In(".").settings(GlobalSettings)
val inLib = In("lib").settings(GlobalSettings)

// the rest
lazy val sharedLib = inLib.as.module.enablePlugins(IdealinguaPlugin)
lazy val justLib = inLib.as.module.enablePlugins(IdealinguaPlugin).depends(sharedLib)


lazy val root = inRoot.as.root
  .transitiveAggregate(
    sharedLib
    , justLib
  )

