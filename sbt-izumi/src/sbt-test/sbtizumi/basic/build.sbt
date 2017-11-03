import sbt.Keys.{pomExtra, publishMavenStyle, scalaVersion}
import ReleaseTransformations._
import IzumiDsl._
import IzumiScopes._
import org.bitbucket.pshirshov.izumi.sbt.definitions.IzumiDsl.RootModule

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

lazy val corelib = Module.in("lib")

// --------------------------------------------
val sharedDefs = globalDefs.withSharedLibs(
  corelib.defaultRef
)
// --------------------------------------------

lazy val testlib = Module.in("lib")

lazy val testUtil = Module.in("lib")
  .depends(testlib)

lazy val root = RootModule.in(".")
  .transitiveAggregate(
    testUtil
  )

