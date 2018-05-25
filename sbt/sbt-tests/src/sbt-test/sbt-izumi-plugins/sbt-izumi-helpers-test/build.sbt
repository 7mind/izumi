import com.github.pshirshov.izumi.sbt.ConvenienceTasksPlugin.Keys._
import com.github.pshirshov.izumi.sbt.deps.{Izumi, IzumiDeps => Iz}

enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)

// -- build settings, root artifact settings, etc
name := "sbt-izumi-helpers-test"
crossScalaVersions in ThisBuild := Seq(
  Iz.V.scala_212
  , "2.11.12"
)

// unfortunately we have to use this bcs conditional settings in plugins don't work
scalacOptions in ThisBuild ++= CompilerOptionsPlugin.dynamicSettings(scalaOrganization.value, scalaVersion.value, isSnapshot.value)
defaultStubPackage := Some("org.test.project")

// -- settings groups identifiers
val AppSettings = new SettingsGroup {

}

// -- settings groups definitions
val GlobalSettings = new SettingsGroup {
  // these settings will be added into each project handled by Izumi
  override val settings: Seq[sbt.Setting[_]] = Seq(
    organization := "com.github.pshirshov.izumi.test"
  )

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

