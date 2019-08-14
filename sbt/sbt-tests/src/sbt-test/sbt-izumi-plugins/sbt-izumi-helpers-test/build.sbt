import IzumiConvenienceTasksPlugin.Keys._

enablePlugins(IzumiEnvironmentPlugin)

// -- build settings, root artifact settings, etc
name := "sbt-izumi-helpers-test"

defaultStubPackage in ThisBuild:= Some("org.test.project")
organization in ThisBuild := "izumi.test.idl"

val legacyScalaVersions = Seq("2.11.12")
val newScalaVersions = Seq(IzumiRootDeps.V.scala_212)
val allScalaVersions = newScalaVersions ++ legacyScalaVersions

val allScala = Seq(crossScalaVersions := allScalaVersions)
val legacyScala = Seq(crossScalaVersions := legacyScalaVersions)
val newScala = Seq(crossScalaVersions := newScalaVersions)

// -- settings groups identifiers
val AppSettings = new SettingsGroup {

}

// -- settings groups definitions
val GlobalSettings = new DefaultGlobalSettingsGroup {
  // these settings will be added into each project handled by Izumi
  override val settings: Seq[sbt.Setting[_]] = Seq()

  // these dependencies will be added into each project handled by Izumi
  override val sharedDeps = Set(
    "com.typesafe" % "config" % "1.3.2"
  )
}


// -- common project directories
lazy val base = Seq(GlobalSettings)
lazy val inRoot = In(".").settingsSeq(base)
lazy val inLibShared = In("lib").settingsSeq(base)

// -- shared definitions (will be added into each project extened with Izumi
lazy val sharedLib = inLibShared.as.module.settings(allScala :_*)
lazy val testOnlySharedLib = inLibShared.as.module.settings(allScala :_*)

// this library definition is not being processed by Izumi
lazy val `non-izumi-shared-lib` = (project in file("lib/non-izumi-shared-lib"))
  .settings(allScala :_*)

lazy val WithShared = new SettingsGroup {
  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    sharedLib.defaultRef // default sbt reference, without test scope inheritance
    , `non-izumi-shared-lib` // test scope inheritance will be applied here
    , testOnlySharedLib.testOnlyRef // this library will be available in all the test scopes
  )
}
lazy val sbase = base ++ Seq(WithShared)
lazy val inLib = In("lib").settingsSeq(sbase)
lazy val inApp = In("app").settingsSeq(sbase).settings(AppSettings)

// the rest
lazy val justLib = inLib.as.module
  .settings(newScala :_*)

lazy val justApp = inApp.as.module
  .depends(justLib)
  .settings(newScala :_*)

lazy val legacyLib = inLib.as.module
  .settings(legacyScala :_*)

lazy val legacyApp = inApp.as.module
  .settings(AppSettings)
  .depends(legacyLib)
  .settings(legacyScala :_*)

lazy val root = inRoot.as.root
  .settings(allScala :_*)
  .transitiveAggregate(
    sharedLib, testOnlySharedLib, `non-izumi-shared-lib`,
    justLib, legacyLib,
    justApp, legacyApp,
  )
  .settings(crossScalaVersions := List()) // https://github.com/sbt/sbt/issues/3465
