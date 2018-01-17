import org.bitbucket.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId._

enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)

// -- build settings, root artifact settings, etc
name := "izumi-r2-test"



val legacyScalaVersions = Seq("2.11.12")
val newScalaVersions = Seq("2.11.12")
val allScalaVersions = legacyScalaVersions ++ newScalaVersions
val allScala = Seq(crossScalaVersions := allScalaVersions)
val legacyScala = Seq(crossScalaVersions := legacyScalaVersions)
val newScala = Seq(crossScalaVersions := newScalaVersions)

//crossScalaVersions in ThisBuild := allScalaVersions


// unfortunately we have to use this bcs conditional settings in plugins don't work
//scalacOptions in ThisBuild ++= CompilerOptionsPlugin.dynamicSettings(scalaVersion.value, isSnapshot.value)

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
lazy val sharedLib = inLib.as.module.settings(allScala :_*)
lazy val testOnlySharedLib = inLib.as.module.settings(allScala :_*)

// this library definition is not being processed by Izumi
lazy val `non-izumi-shared-lib` = (project in file("lib/non-izumi-shared-lib"))
  .settings(allScala :_*)

val sharedDefs = globalDefs.withSharedLibs(
  sharedLib.defaultRef // default sbt reference, without test scope inheritance
  , `non-izumi-shared-lib` // test scope inheritance will be applied here
  , testOnlySharedLib.testOnlyRef // this library will be available in all the test scopes
)

// the rest
lazy val justLib = inLib.as.module.settings(newScala :_*)

lazy val justApp: Project = inApp.as.module
  .depends(justLib)
  .settings(AppSettings)
  .settings(newScala :_*)

lazy val legacyLib = inLib.as.module.settings(legacyScala :_*)

lazy val legacyApp: Project = inApp.as.module
  .settings(AppSettings)
  .settings(legacyScala :_*)

lazy val root = inRoot.as.root
  .transitiveAggregate(
    justApp
  )

