import org.bitbucket.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId._
import sbt.Keys.{pomExtra, publishMavenStyle}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

// TODO: library descriptor generator
// TODO: better analyzer for "exposed" scope
// TODO: config -- probably we don't need it
// TODO: conditionals in plugins: release settings, integration tests -- impossible

enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)
enablePlugins(GitStampPlugin)

name := "izumi-r2"

val AppSettings = SettingsGroupId()

val scala_212 = "2.12.4"
val scala_213 = "2.13.0-M2"

scalacOptions in ThisBuild ++= CompilerOptionsPlugin.dynamicSettings(scalaVersion.value, isSnapshot.value)

val baseSettings = new GlobalSettings {
  override protected val settings: Map[SettingsGroupId, ProjectSettings] = Map(
    GlobalSettingsGroup -> new ProjectSettings {
      override val settings: Seq[sbt.Setting[_]] = Seq(
        organization := "com.github.pshirshov.izumi.r2"
        , crossScalaVersions := Seq(
          scala_212
        )
        , publishMavenStyle in Global := true
        , sonatypeProfileName := "com.github.pshirshov"
        , publishTo := Some(
          if (isSnapshot.value)
            Opts.resolver.sonatypeSnapshots
          else
            Opts.resolver.sonatypeStaging
        )
        , credentials in Global += Credentials(new File("credentials.sonatype-nexus.properties"))
        , pomExtra in Global := <url>https://bitbucket.org/pshirshov/izumi-r2</url>
          <licenses>
            <license>
              <name>BSD-style</name>
              <url>http://www.opensource.org/licenses/bsd-license.php</url>
              <distribution>repo</distribution>
            </license>
          </licenses>
          <developers>
            <developer>
              <id>pshirshov</id>
              <name>Pavel Shirshov</name>
              <url>https://github.com/pshirshov</url>
            </developer>
          </developers>

        , releaseProcess := Seq[ReleaseStep](
          checkSnapshotDependencies, // : ReleaseStep
          inquireVersions, // : ReleaseStep
          runClean, // : ReleaseStep
          runTest, // : ReleaseStep
          setReleaseVersion, // : ReleaseStep
          commitReleaseVersion, // : ReleaseStep, performs the initial git checks
          tagRelease, // : ReleaseStep
          //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
          setNextVersion, // : ReleaseStep
          commitNextVersion, // : ReleaseStep
          pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
        )
      )
    }
  )
}

// --------------------------------------------
val globalDefs = setup(baseSettings)
// --------------------------------------------

val inRoot = In(".")
val inLib = In("lib")


lazy val sbtIzumi = inRoot.as
  .module
  .enablePlugins(ScriptedPlugin)
  .settings(
    target ~= { t => t.toPath.resolve("primary").toFile }
    , scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    }
    , scriptedBufferLog := false
    , crossScalaVersions := Seq(
      scala_212
    )
  )

lazy val logMacros: Project = inLib.as.module.enablePlugins(ScriptedPlugin)

val logger = inLib.as.module.enablePlugins(ScriptedPlugin).settings(
  target ~= { t => t.toPath.resolve("primary").toFile }
  , scriptedLaunchOpts := {
    scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
  }
  , scriptedBufferLog := false
  , crossScalaVersions := Seq(
    scala_212
  )
).dependsOn(logMacros)

lazy val root = inRoot.as
  .root
  .transitiveAggregate(
    sbtIzumi
  )

