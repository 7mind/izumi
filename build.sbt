import sbt.Keys.{pomExtra, publishMavenStyle, scalaVersion}
import ReleaseTransformations._
import IzumiDsl._
import IzumiScopes._
import org.bitbucket.pshirshov.izumi.sbt.definitions.{ProjectSettings, SettingsGroupId}
import sbt.ScriptedPlugin._
import D._

// TODO: library descriptor generator
// TODO: better analyzer for "exposed" scope
// TODO: config -- probably we don't need it
// TODO: conditionals in plugins: release settings, integration tests -- impossible

enablePlugins(ConvenienceTasksPlugin)

name := "izumi-r2"

val LibSettings = SettingsGroupId()


val baseSettings = new GlobalSettings {
  override val globalSettings: ProjectSettings = new ProjectSettings {
      override val settings = Seq(
        organization := "com.github.pshirshov.izumi"
        , scalaVersion := "2.12.4"
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

  override val customSettings: Map[SettingsGroupId, ProjectSettings] = Map(
    LibSettings -> new ProjectSettings {
      override val settings: Seq[sbt.Setting[_]] = Seq(
        Seq(
          libraryDependencies ++= R.essentials
          , libraryDependencies ++= T.essentials
        )
      ).flatten
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
  )

lazy val di = inLib.as.module
    .settings(
      libraryDependencies += R.scala_reflect
    )
    .extend(LibSettings)

lazy val root = inRoot.as
  .root
  .enablePlugins(GitStampPlugin)
  .transitiveAggregate(
    sbtIzumi, di
  )

