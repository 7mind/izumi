import D._
import com.github.pshirshov.izumi.sbt.ConvenienceTasksPlugin.Keys.defaultStubPackage
import com.github.pshirshov.izumi.sbt.IzumiScopesPlugin.ProjectReferenceEx
import com.github.pshirshov.izumi.sbt.IzumiSettingsGroups.autoImport.SettingsGroupId._
import com.typesafe.sbt.pgp.PgpSettings
import coursier.ShadingPlugin.autoImport.shadingNamespace
import sbt.Keys.{pomExtra, publishMavenStyle}
import sbtassembly.Assembly
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._


enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)
enablePlugins(GitStampPlugin)
disablePlugins(AssemblyPlugin)

name := "izumi-r2"

val AppSettings = SettingsGroupId()
val LibSettings = SettingsGroupId()
val SbtSettings = SettingsGroupId()
val ShadingSettings = SettingsGroupId()

val scala_212 = "2.12.4"
val scala_213 = "2.13.0-M3"

scalacOptions in ThisBuild ++= CompilerOptionsPlugin.dynamicSettings(scalaOrganization.value, scalaVersion.value, isSnapshot.value)
defaultStubPackage := Some("com.github.pshirshov.izumi")

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
        , addCompilerPlugin(R.kind_projector)
      )

    }
    , LibSettings -> new ProjectSettings {
      override val settings: Seq[sbt.Setting[_]] = Seq(
        Seq(
          libraryDependencies ++= R.essentials
          , libraryDependencies ++= T.essentials
        )
      ).flatten

      override val disabledPlugins: Set[AutoPlugin] = Set(AssemblyPlugin)
    }
    , ShadingSettings -> new ProjectSettings {
      override val plugins: Set[Plugins] = Set(ShadingPlugin)

      override val settings: Seq[sbt.Setting[_]] = Seq(
        inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings ++ PublishingPlugin.projectSettings) ++
          _root_.coursier.ShadingPlugin.projectSettings ++
          Seq(
            publish := publish.in(Shading).value
            , publishLocal := publishLocal.in(Shading).value
            , PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value
            , PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
            , shadingNamespace := "izumi.shaded"
            , shadeNamespaces ++= Set(
              "fastparse"
              , "sourcecode"
              //            , "net.sf.cglib"
              //            , "org.json4s"
            )
          )
      ).flatten
    }
    , SbtSettings -> new ProjectSettings {
      override val plugins: Set[Plugins] = Set(ScriptedPlugin)
      override val disabledPlugins: Set[AutoPlugin] = Set(AssemblyPlugin)

      override val settings: Seq[sbt.Setting[_]] = Seq(
        Seq(
          target ~= { t => t.toPath.resolve("primary").toFile }
          , scriptedLaunchOpts := {
            scriptedLaunchOpts.value ++
              Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
          }
          , scriptedBufferLog := false

          , crossScalaVersions := Seq(
            scala_212
          )
          , libraryDependencies ++= Seq(
            "org.scala-sbt" % "sbt" % sbtVersion.value
          )
          , sbtPlugin := true
        )
      ).flatten
    }
    , AppSettings -> new ProjectSettings {
      override val plugins = Set(AssemblyPlugin)
    }
  )
}
// --------------------------------------------

val inRoot = In(".")
val inShade = In("shade")

val inSbt = In("sbt")
  .withModuleSettings(SbtSettings)
val inDiStage = In("distage")
  .withModuleSettings(LibSettings)
val inLogStage = In("logstage")
  .withModuleSettings(LibSettings)
val inFundamentals = In("fundamentals")
  .withModuleSettings(LibSettings)
val inIdealinguaBase = In("idealingua")
val inIdealingua = inIdealinguaBase
  .withModuleSettings(LibSettings)


// --------------------------------------------

lazy val fundamentalsCollections = inFundamentals.as.module
lazy val fundamentalsPlatform = inFundamentals.as.module
lazy val fundamentalsFunctional = inFundamentals.as.module

lazy val fundamentals: Seq[ProjectReferenceEx] = Seq(
  fundamentalsCollections
  , fundamentalsPlatform
  , fundamentalsFunctional
)
// --------------------------------------------
val globalDefs = setup(baseSettings)
  .withSharedLibs(fundamentals: _*)
// --------------------------------------------

lazy val fundamentalsReflection = inFundamentals.as.module
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect
    )
  )

lazy val distageModel = inDiStage.as.module
  .depends(fundamentalsReflection)

lazy val distageMacro = inDiStage.as.module
  .depends(distageModel)
  .settings(
    libraryDependencies ++= Seq(R.scala_reflect)
  )

lazy val distageCore = inDiStage.as.module
  .depends(distageMacro, fundamentalsFunctional)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect
      , R.cglib_nodep
    )
  )

//
lazy val logstageModel = inLogStage.as.module

lazy val logstageMacro = inLogStage.as.module
  .depends(logstageModel)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect
    )
  )

lazy val logstageApi = inLogStage.as.module
  .depends(logstageMacro)

lazy val logstageSinkConsole = inLogStage.as.module
  .depends(logstageApi)

lazy val logstageAdapterSlf4j = inLogStage.as.module
  .depends(logstageApi)
  .settings(libraryDependencies += R.slf4j_api)
//

lazy val logstageDi = inLogStage.as.module
  .depends(logstageApi, distageModel)

lazy val logstageJsonJson4s = inLogStage.as.module
  .depends(logstageApi)
  .settings(libraryDependencies ++= Seq(R.json4s_native))

lazy val logstageSinkFile = inLogStage.as.module
  .depends(logstageApi)



lazy val logstageSinkSlf4j = inLogStage.as.module
  .depends(logstageApi)
  .settings(libraryDependencies ++= Seq(R.slf4j_api, T.slf4j_simple))

lazy val logstageRouting = inLogStage.as.module
  .depends(logstageApi)
  .depends(Seq(
    logstageSinkConsole
    , logstageSinkSlf4j
    , logstageJsonJson4s
  ).map(_.testOnlyRef): _*)

lazy val idealinguaModel = inIdealingua.as.module
  .settings()

lazy val idealinguaRuntimeRpc = inIdealingua.as.module

lazy val idealinguaTestDefs = inIdealingua.as.module.dependsOn(idealinguaRuntimeRpc, idealinguaRuntimeRpcCirce)

lazy val idealinguaRuntimeRpcCirce = inIdealingua.as.module
  .depends(idealinguaRuntimeRpc)
  .settings(libraryDependencies ++= R.circe)

lazy val idealinguaRuntimeRpcCats = inIdealingua.as.module
  .depends(idealinguaRuntimeRpc)
  .settings(libraryDependencies ++= R.cats_all)


lazy val idealinguaRuntimeRpcHttp4s = inIdealingua.as.module
  .depends(idealinguaRuntimeRpcCirce, idealinguaRuntimeRpcCats)
  .depends(Seq(idealinguaTestDefs).map(_.testOnlyRef): _*)
  .settings(libraryDependencies ++= R.http4s_all)


lazy val fastparseShaded = inShade.as.module
  .settings(libraryDependencies ++= Seq(R.fastparse))
  .settings(ShadingSettings)

lazy val idealinguaCore = inIdealingua.as.module
  .settings(libraryDependencies ++= Seq(R.scala_reflect, R.scalameta) ++ Seq(T.scala_compiler, T.scala_library))
  .depends(idealinguaModel, idealinguaRuntimeRpc, fastparseShaded)
  .depends(Seq(idealinguaTestDefs).map(_.testOnlyRef): _*)
  .settings(ShadingSettings)

lazy val idealinguaExtensionRpcFormatCirce = inIdealingua.as.module
  .depends(idealinguaCore, idealinguaRuntimeRpcCirce)
  .depends(Seq(idealinguaTestDefs).map(_.testOnlyRef): _*)


lazy val idealinguaCompiler = inIdealinguaBase.as.module
  .depends(idealinguaCore, idealinguaExtensionRpcFormatCirce)
  .settings(AppSettings)
  .settings(
    libraryDependencies ++= Seq(R.scallop)
    , mainClass in assembly := Some("com.github.pshirshov.izumi.idealingua.compiler.CliIdlCompiler")
  )
  .settings(addArtifact(artifact in(Compile, assembly), assembly))


lazy val sbtIzumi = inSbt.as
  .module

lazy val sbtIdealingua = inSbt.as
  .module
  .depends(idealinguaCore, idealinguaExtensionRpcFormatCirce)

lazy val sbtTests = inSbt.as
  .module
  .depends(sbtIzumi, sbtIdealingua)

lazy val logstage: Seq[ProjectReference] = Seq(
  logstageDi
  , logstageRouting
  , logstageSinkConsole
  , logstageSinkFile
  , logstageSinkSlf4j
  , logstageAdapterSlf4j
)
lazy val distage: Seq[ProjectReference] = Seq(
  distageCore
)
lazy val idealingua: Seq[ProjectReference] = Seq(
  idealinguaCore
  , idealinguaRuntimeRpc
  , idealinguaRuntimeRpcHttp4s
  , idealinguaRuntimeRpcCats
  , idealinguaRuntimeRpcCirce
  , idealinguaExtensionRpcFormatCirce
  , idealinguaCompiler
)
lazy val izsbt: Seq[ProjectReference] = Seq(
  sbtIzumi, sbtIdealingua, sbtTests
)

lazy val allProjects = distage ++ logstage ++ idealingua ++ izsbt

lazy val `izumi-r2` = inRoot.as
  .root
  .transitiveAggregate(allProjects: _*)


