import _root_.org.bitbucket.pshirshov.izumi.sbt.GitStampPlugin
import _root_.org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjects._
import _root_.org.bitbucket.pshirshov.izumi.sbt.definitions.ExtendedProjectsGlobalDefs._
import _root_.org.bitbucket.pshirshov.izumi.sbt.definitions._
import sbt.Keys.{pomExtra, publishMavenStyle, scalaVersion, version}
import ReleaseTransformations._

// conditionals in plugins: release settings, integration tests -- impossible
// config

name := "izumi-r2"


val settings = new GlobalSettings {
  override val globalSettings: Seq[sbt.Setting[_]] = Seq(
    organization := "org.bitbucket.pshirshov.izumi"
    , scalaVersion := "2.12.4"
    , publishMavenStyle in Global := true
    , sonatypeProfileName := "org.bitbucket.pshirshov"
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
          <url>http://pshirshov.me</url>
        </developer>
      </developers>

     , releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,              // : ReleaseStep
      inquireVersions,                        // : ReleaseStep
      runClean,                               // : ReleaseStep
      runTest,                                // : ReleaseStep
      setReleaseVersion,                      // : ReleaseStep
      commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
      tagRelease,                             // : ReleaseStep
      //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
      setNextVersion,                         // : ReleaseStep
      commitNextVersion,                      // : ReleaseStep
      pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
    )
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
  .settings(publishArtifact := false)

// --------------------------------------------
val sharedDefs = globalDefs.withSharedLibs(
  corelib.defaultRef
)
// --------------------------------------------

lazy val testlib = Module.in("lib")
  .settings(publishArtifact := false)

lazy val `test-util` = Module.in("lib")
  .depends(testlib)
  .settings(publishArtifact := false)

lazy val root = RootModule.in(".")
  .enablePlugins(GitStampPlugin)
  .transitiveAggregate(
    `test-util`
    , `sbt-izumi`
  )


