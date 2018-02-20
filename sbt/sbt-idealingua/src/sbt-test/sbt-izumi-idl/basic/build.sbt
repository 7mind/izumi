import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId._
import com.github.pshirshov.izumi.sbt.ConvenienceTasksPlugin.Keys._

enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)

// -- build settings, root artifact settings, etc
name := "izumi-idl-test"
crossScalaVersions in ThisBuild := Seq(
  "2.12.4"
)

// unfortunately we have to use this bcs conditional settings in plugins don't work
scalacOptions in ThisBuild ++= CompilerOptionsPlugin.dynamicSettings(scalaVersion.value, isSnapshot.value)
defaultStubPackage := Some("org.test.project")

// -- settings groups identifiers
val AppSettings = SettingsGroupId()

// -- settings groups definitions
val baseSettings = new GlobalSettings {
  override val settings: Map[SettingsGroupId, ProjectSettings] = Map(
    GlobalSettingsGroup -> new ProjectSettings {
      // these settings will be added into each project handled by Izumi
      override val settings: Seq[sbt.Setting[_]] = Seq(
        organization := "com.github.pshirshov.izumi.test.idl"
      )

      // these dependencies will be added into each project handled by Izumi
      override val sharedDeps = Set(
        "com.github.pshirshov.izumi.r2" %% "idealingua-model" % sys.props("plugin.version")
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

// the rest
lazy val justLib = inLib.as.module.enablePlugins(IdealinguaPlugin)


lazy val root = inRoot.as.root
  .transitiveAggregate(
    justLib
  )

