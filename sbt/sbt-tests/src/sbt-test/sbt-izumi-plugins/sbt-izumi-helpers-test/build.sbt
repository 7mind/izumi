import com.github.pshirshov.izumi.sbt.deps.{Izumi, IzumiDeps => Iz}

import IzumiConvenienceTasksPlugin.Keys._

enablePlugins(IzumiEnvironmentPlugin)

// -- build settings, root artifact settings, etc
name := "sbt-izumi-helpers-test"
crossScalaVersions in ThisBuild := Seq(
  Iz.V.scala_212
  , "2.11.12"
)

defaultStubPackage in ThisBuild:= Some("org.test.project")
organization in ThisBuild := "com.github.pshirshov.izumi.test.idl"

// -- settings groups identifiers
val AppSettings = new SettingsGroup {

}

// -- settings groups definitions
val GlobalSettings = new SettingsGroup {
  override val plugins = Set(IzumiCompilerOptionsPlugin, IzumiPublishingPlugin)

  // these settings will be added into each project handled by Izumi
  override val settings: Seq[sbt.Setting[_]] = Seq()

  // these dependencies will be added into each project handled by Izumi
  override val sharedDeps = Set(
    "com.typesafe" % "config" % "1.3.2"
  )
}

// settings groups are saved in


// -- common project directories
lazy val base = Seq(GlobalSettings)
lazy val inRoot = In(".").settingsSeq(base)
lazy val inLibShared = In("lib").settingsSeq(base)
lazy val sbase = base ++ Seq(WithShared)
lazy val inLib = In("lib").settingsSeq(sbase)
lazy val inApp = In("app").settingsSeq(sbase).settings(AppSettings)

// -- shared definitions (will be added into each project extened with Izumi
lazy val sharedLib = inLibShared.as.module
lazy val testOnlySharedLib = inLibShared.as.module

// this library definition is not being processed by Izumi
lazy val `non-izumi-shared-lib` = project in file("lib/non-izumi-shared-lib")

lazy val WithShared = new SettingsGroup {
  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    sharedLib.defaultRef // default sbt reference, without test scope inheritance
    , `non-izumi-shared-lib` // test scope inheritance will be applied here
    , testOnlySharedLib.testOnlyRef // this library will be available in all the test scopes
  )
}

// the rest
lazy val justLib = inLib.as.module

lazy val justApp = inApp.as.module
  .depends(justLib)

lazy val root = inRoot.as.root
  .transitiveAggregate(
    justApp, `non-izumi-shared-lib`
  )

