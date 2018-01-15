import sbt.Keys.{pomExtra, publishMavenStyle, scalaVersion}
import ReleaseTransformations._
import SettingsGroupId._

enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)

// -- build settings, root artifact settings, etc
name := "izumi-r2-test"

// -- settings groups identifiers
val AppSettings = SettingsGroupId()

// -- settings groups definitions
val baseSettings = new GlobalSettings {
  override val settings: Map[SettingsGroupId, ProjectSettings] = Map(
    GlobalSettingsGroup -> new ProjectSettings {
      // these settings will be added into each project handled by Izumi
      override val settings: Seq[sbt.Setting[_]] = Seq(
        organization := "com.github.pshirshov.izumi.test"
      )

      // these dependencies will be added into each project handled by Izumi
      override val sharedDeps = Set(
        "com.typesafe" % "config" % "1.3.2"
      )
    }
    , AppSettings -> new ProjectSettings {

    }
  )
}

// settings groups are saved in
val globalDefs = setup(baseSettings)

// -- common project directories
val inRoot = In(".")
val inLib = In("lib")
val inApp = In("app")

// -- shared definitions (will be added into each project extened with Izumi
lazy val sharedLib = inLib.as.module
lazy val testOnlySharedLib = inLib.as.module

// this library definition is not being processed by Izumi
lazy val `non-izumi-shared-lib` = project in file("lib/non-izumi-shared-lib")

val sharedDefs = globalDefs.withSharedLibs(
  sharedLib.defaultRef            // default sbt reference, without test scope inheritance
  , `non-izumi-shared-lib`        // test scope inheritance will be applied here
  , testOnlySharedLib.testOnlyRef // this library will be available in all the test scopes
)

// the rest
lazy val justLib = inLib.as.module

lazy val justApp = inApp.as.module
  .depends(justLib)
  .settings(AppSettings)

lazy val root = inRoot.as.root
  .transitiveAggregate(
    justApp
  )

