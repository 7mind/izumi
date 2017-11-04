import sbt.Keys.{pomExtra, publishMavenStyle, scalaVersion}
import ReleaseTransformations._
import IzumiDsl._
import IzumiScopes._

enablePlugins(ConvenienceTasksPlugin)

name := "izumi-r2-test"
version in ThisBuild := "0.1.0-SNAPSHOT"

val settings = new GlobalSettings {
  override val globalSettings: Seq[sbt.Setting[_]] = Seq(
    organization := "com.github.pshirshov.izumi.test"
    , scalaVersion := "2.12.4"
  )

  override val sharedDeps = Set(
    "com.typesafe" % "config" % "1.3.2"
  )
}

// --------------------------------------------
val globalDefs = new GlobalDefs(settings)
// --------------------------------------------

val inRoot = In(".")
val inLib = In("lib")


lazy val corelib = inLib.as.module

// --------------------------------------------
val sharedDefs = globalDefs.withSharedLibs(
  corelib.defaultRef
)
// --------------------------------------------

lazy val testlib = inLib.as.module

lazy val testUtil = inLib.as.module
  .depends(testlib)

lazy val root = inRoot.as.root
  .transitiveAggregate(
    testUtil
  )

