import _root_.org.bitbucket.pshirshov.izumi.sbt.GitStampPlugin
import _root_.org.bitbucket.pshirshov.izumi.sbt.definitions._
import _root_.org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjects._
import _root_.org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjectsGlobalDefs._
import sbt.Keys.{scalaVersion, version}

// conditionals in plugins: release settings, integration tests -- impossible
// config

name := "izumi-r2"

val settings = new GlobalSettings {
  override val globalSettings: Seq[sbt.Setting[_]] = Seq(
    organization := "org.bitbucket.pshirshov.izumi"
    , version := "0.1.0-SNAPSHOT"
    , scalaVersion := "2.12.4"
  )

  override val sharedDeps = Set(
    "com.typesafe" % "config" % "1.3.2"
  )
}

// --------------------------------------------
val globalDefs = new GlobalDefs(settings)
// --------------------------------------------

lazy val `sbt-izumi` = ConfiguredModule.in(".")
  .settings(
    target ~= { t => t.toPath.resolve("primary").toFile }
  )

lazy val corelib = Module.in("lib")

// --------------------------------------------
val sharedDefs = globalDefs.withSharedLibs(
  corelib.defaultRef
)
// --------------------------------------------

lazy val testlib = Module.in("lib")

lazy val `test-util` = Module.in("lib")
  .depends(testlib)

lazy val root = RootModule.in(".")
  .enablePlugins(GitStampPlugin)
  .transitiveAggregate(
    `test-util`
    , `sbt-izumi`
  )


