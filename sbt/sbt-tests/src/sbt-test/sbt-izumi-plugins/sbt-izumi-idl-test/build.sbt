import sbt.Keys._
import com.github.pshirshov.izumi.sbt.deps.{Izumi, IzumiDeps => Iz}
import SettingsGroupId._
import SbtConvenienceTasks.Keys._

enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)

lazy val pluginVersion = if (sys.props.isDefinedAt("plugin.version")) {
  sys.props("plugin.version")
} else {
  IO.read(new File("../../../../../../version.sbt")).split("\"")(1)
}

// -- build settings, root artifact settings, etc
name := "sbt-izumi-idl-test"
crossScalaVersions in ThisBuild := Seq(
  Iz.V.scala_212
)

// unfortunately we have to use this bcs conditional settings in plugins don't work
scalacOptions in ThisBuild ++= CompilerOptionsPlugin.dynamicSettings(scalaOrganization.value, scalaVersion.value, isSnapshot.value)
defaultStubPackage := Some("org.test.project")

// -- settings groups identifiers
val AppSettings = new SettingsGroup {

}

val GlobalSettings = new SettingsGroup {
  // these settings will be added into each project handled by Izumi
  override val settings: Seq[sbt.Setting[_]] = Seq(
    organization := "com.github.pshirshov.izumi.test.idl"
  )

  // these dependencies will be added into each project handled by Izumi
  override val sharedDeps = Set(
    Izumi.R.idealingua_model
    , Izumi.R.idealingua_runtime_rpc_http4s
    , Izumi.R.idealingua_runtime_rpc_circe
    , Izumi.R.idealingua_runtime_rpc_cats
  )
}

// settings groups are saved in
val globalDefs = setup(GlobalSettings)

// -- common project directories
val inRoot = In(".")
val inLib = In("lib")

// the rest
lazy val sharedLib = inLib.as.module.enablePlugins(IdealinguaPlugin)
lazy val justLib = inLib.as.module.enablePlugins(IdealinguaPlugin).depends(sharedLib)


lazy val root = inRoot.as.root
  .transitiveAggregate(
    sharedLib
    , justLib
  )

