import com.github.pshirshov.izumi.sbt.deps.IzumiDeps.{R, _}
import com.github.pshirshov.izumi.sbt.plugins.IzumiConvenienceTasksPlugin.Keys._
import com.github.pshirshov.izumi.sbt.plugins.optional.IzumiPublishingPlugin.Keys._
import com.typesafe.sbt.pgp.PgpSettings
import sbt.Keys.{publishMavenStyle, sourceDirectory}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

enablePlugins(IzumiGitEnvironmentPlugin)
disablePlugins(AssemblyPlugin, ScriptedPlugin)

name := "izumi-r2"
organization in ThisBuild := "com.github.pshirshov.izumi.r2"
defaultStubPackage in ThisBuild := Some("com.github.pshirshov.izumi")
publishMavenStyle in ThisBuild := true
homepage in ThisBuild := Some(url("https://izumi.7mind.io"))
licenses in ThisBuild := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))
developers in ThisBuild := List(
  Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/pshirshov"), email = "team@7mind.io"),
)

releaseProcess := Seq[ReleaseStep](
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

publishTargets in ThisBuild := Repositories.typical("sonatype-nexus", sonatypeTarget.value.root)

val GlobalSettings = new DefaultGlobalSettingsGroup {
  override val id = SettingsGroupId("GlobalSettings")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    crossScalaVersions := Seq(
      V.scala_212,
      V.scala_213,
    )
    , scalaVersion := crossScalaVersions.value.head
    , sonatypeProfileName := "com.github.pshirshov"
    , addCompilerPlugin(R.kind_projector)
  )
}

val AppSettings = new SettingsGroup {
  override val id = SettingsGroupId("AppSettings")

  override val disabledPlugins: Set[AutoPlugin] = Set(SitePlugin)
  override val plugins = Set(AssemblyPlugin)
}


val LibSettings = new SettingsGroup {
  override val id = SettingsGroupId("LibSettings")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      libraryDependencies ++= R.essentials
      , libraryDependencies ++= T.essentials
    )
  ).flatten
}

val SbtSettings = new SettingsGroup {
  override val id = SettingsGroupId("SbtSettings")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      target ~= { t => t.toPath.resolve("primary").toFile }
      , crossScalaVersions := Seq(
        V.scala_212
      )
      , libraryDependencies ++= Seq(
        "org.scala-sbt" % "sbt" % sbtVersion.value
      )
      , sbtPlugin := true
    )
  ).flatten
}

val ShadingSettings = new SettingsGroup {
  override val id = SettingsGroupId("ShadingSettings")

  override val plugins: Set[Plugins] = Set(ShadingPlugin)

  override val settings: Seq[sbt.Setting[_]] = Seq(
    inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings ++ IzumiPublishingPlugin.projectSettings) ++
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
        )
      )
  ).flatten
}

val WithoutBadPlugins = new SettingsGroup {
  override val id = SettingsGroupId("WithoutBadPlugins")

  override val disabledPlugins: Set[AutoPlugin] = Set(AssemblyPlugin, SitePlugin, ScriptedPlugin)

}

val WithoutBadPluginsSbt = new SettingsGroup {
  override val id = SettingsGroupId("WithoutBadPluginsSbt")

  override val disabledPlugins: Set[AutoPlugin] = Set(AssemblyPlugin, SitePlugin)

}


val SbtScriptedSettings = new SettingsGroup {
  override val id = SettingsGroupId("SbtScriptedSettings")

  override val plugins: Set[Plugins] = Set(ScriptedPlugin)

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      }
      , scriptedBufferLog := false
    )
  ).flatten
}

// --------------------------------------------

lazy val inRoot = In(".")
  .settings(GlobalSettings)

lazy val inDoc = In("doc")
  .settings(GlobalSettings)

lazy val base = Seq(GlobalSettings, LibSettings, WithoutBadPlugins)

lazy val inFundamentals = In("fundamentals")
  .settingsSeq(base)

lazy val inShade = In("shade")
  .settings(ShadingSettings)
  .settingsSeq(base)

lazy val inSbt = In("sbt")
  .settings(GlobalSettings, WithoutBadPluginsSbt)
  .settings(SbtSettings, SbtScriptedSettings)

lazy val inDiStage = In("distage")
  .settingsSeq(base)
  .settings(WithFundamentals)

lazy val inLogStage = In("logstage")
  .settingsSeq(base)
  .settings(WithFundamentals)

lazy val inIdealinguaBase = In("idealingua")
  .settings(GlobalSettings, WithFundamentals)

lazy val inIdealinguaBaseX = In("idealingua")
  .settings(GlobalSettings, WithFundamentalsX)

lazy val inIdealingua = inIdealinguaBase
  .settingsSeq(base)
  .settings(WithFundamentals)

lazy val inIdealinguaX = inIdealinguaBaseX
  .settingsSeq(base)
  .settings(WithFundamentalsX)

val platforms = Seq(JVMPlatform, JSPlatform)

// --------------------------------------------

lazy val fundamentalsCollections = inFundamentals.as.cross(platforms)
lazy val fundamentalsCollectionsJvm = fundamentalsCollections.jvm.remember
lazy val fundamentalsCollectionsJs = fundamentalsCollections.js.remember

lazy val fundamentalsPlatform = inFundamentals.as.cross(platforms)
  .dependsOn(fundamentalsCollections)
lazy val fundamentalsPlatformJvm = fundamentalsPlatform.jvm.remember
lazy val fundamentalsPlatformJs = fundamentalsPlatform.js.remember

lazy val fundamentalsFunctional = inFundamentals.as.cross(platforms)
lazy val fundamentalsFunctionalJvm = fundamentalsFunctional.jvm.remember
lazy val fundamentalsFunctionalJs = fundamentalsFunctional.js.remember

lazy val fundamentalsBio = inFundamentals.as.cross(platforms)
  .dependsOn(fundamentalsFunctional)
  .settings(
    libraryDependencies ++= (R.zio_core +: R.cats_all).map(_.cross(platformDepsCrossVersion.value) % Optional)
  )
lazy val fundamentalsBioJvm = fundamentalsBio.jvm.remember
lazy val fundamentalsBioJs = fundamentalsBio.js.remember


lazy val WithFundamentals = new SettingsGroup {
  override val id = SettingsGroupId("WithFundamentals")

  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    fundamentalsCollectionsJvm
    , fundamentalsPlatformJvm
    , fundamentalsFunctionalJvm
  )
}

lazy val WithFundamentalsX = new SettingsGroup {
  override val id = SettingsGroupId("WithFundamentalsX")

  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    fundamentalsCollections
    , fundamentalsPlatform
    , fundamentalsFunctional
  )
}
// --------------------------------------------

lazy val fundamentalsTypesafeConfig = inFundamentals.as.module
  .depends(fundamentalsReflection)
  .settings(
    libraryDependencies ++= Seq(
      R.typesafe_config
    )
  )

lazy val fundamentalsReflection = inFundamentals.as.module
  .depends(fundamentalsPlatformJvm)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
    )
  )

lazy val distageModel = inDiStage.as.module
  .depends(
    fundamentalsReflection,
    fundamentalsBioJvm,
  )
  .settings(
    libraryDependencies ++= R.cats_all.map(_ % Optional),
  )

lazy val distageProxyCglib = inDiStage.as.module
  .depends(distageModel)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
      , R.cglib_nodep
    )
  )

lazy val distageConfig = inDiStage.as.module
  .depends(
    distageCore,
    fundamentalsTypesafeConfig,
    logstageRenderingCirce,
    logstageAdapterSlf4j,
    logstageDi,
  )
  .settings(
    libraryDependencies ++= Seq(
      R.typesafe_config
    )
  )

lazy val distagePlugins = inDiStage.as.module
  .depends(distageCore, distageConfig.testOnlyRef)
  .settings(
    libraryDependencies ++= Seq(R.fast_classpath_scanner)
  )

lazy val distageApp = inDiStage.as.module
  .depends(distageCore, distagePlugins, distageConfig)

lazy val distageRolesApi = inDiStage.as.module
  .depends(distageCore, distagePlugins)

lazy val distageRoles = inDiStage.as.module
  .depends(distageRolesApi, distageApp)

lazy val distageRolesScalaopt = inDiStage.as.module
  .depends(distageRoles)
  .settings(
    libraryDependencies += R.scopt
  )

lazy val distageCore = inDiStage.as.module
  .depends(fundamentalsFunctionalJvm, distageModel, distageProxyCglib)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
    )
  )

lazy val distageTestkit = inDiStage.as.module
  .depends(distageCore, distagePlugins, distageConfig, distageRoles, logstageDi)
  .settings(
    libraryDependencies ++= Seq(R.scalatest, R.scalacheck, R.scalacheck_shapeless)
  )

lazy val distageCats = inDiStage.as.module
  .depends(distageCore)
  .settings(
    libraryDependencies ++= R.cats_all
  )

lazy val distageStatic = inDiStage.as.module
  .depends(distageCore, distageApp.testOnlyRef)
  .settings(
    libraryDependencies += R.shapeless
  )

//-----------------------------------------------------------------------------

lazy val logstageApi = inLogStage.as.module
  .depends(fundamentalsReflection)

lazy val logstageCore = inLogStage.as.module
  .depends(logstageApi, fundamentalsBioJvm)

lazy val logstageDi = inLogStage.as.module
  .depends(
    logstageCore
    , distageModel
    , distageCore.testOnlyRef
  )

lazy val logstageConfig = inLogStage.as.module
  .depends(fundamentalsTypesafeConfig, logstageCore)

lazy val logstageConfigDi = inLogStage.as.module
  .depends(logstageConfig, distageConfig)

lazy val logstageAdapterSlf4j = inLogStage.as.module
  .depends(logstageCore)
  .settings(
    libraryDependencies += R.slf4j_api
    , compileOrder in Compile := CompileOrder.Mixed
    , compileOrder in Test := CompileOrder.Mixed
  )

lazy val logstageRenderingCirce = inLogStage.as.module
  .depends(logstageCore)
  .settings(libraryDependencies ++= R.circe)

lazy val logstageSinkSlf4j = inLogStage.as.module
  .depends(
    logstageApi
    , logstageCore.testOnlyRef
  )
  .settings(libraryDependencies ++= Seq(R.slf4j_api, T.slf4j_simple))
//-----------------------------------------------------------------------------

lazy val fastparseShaded = inShade.as.module
  .settings(libraryDependencies ++= Seq(R.fastparse % "shaded"))

lazy val idealinguaModel = inIdealinguaX.as.cross(platforms)
lazy val idealinguaModelJvm = idealinguaModel.jvm.remember
lazy val idealinguaModelJs = idealinguaModel.js.remember

lazy val idealinguaCore = inIdealinguaX.as.cross(platforms)
  .depends(idealinguaModel)
lazy val idealinguaCoreJvm = idealinguaCore.jvm.remember
  .depends(fastparseShaded)
  .settings(ShadingSettings)
lazy val idealinguaCoreJs = idealinguaCore.js.remember
  .settings(libraryDependencies ++= Seq(R.fastparse).map(_.cross(platformDepsCrossVersion.value)))

lazy val idealinguaRuntimeRpcScala = inIdealinguaX.as.cross(platforms)
  .dependsOn(fundamentalsBio)
  .settings(
    libraryDependencies ++= R.circe.map(_.cross(platformDepsCrossVersion.value)),
    libraryDependencies ++= (R.zio_core +: R.zio_interop +: R.cats_all).map(_.cross(platformDepsCrossVersion.value))
  )

lazy val idealinguaRuntimeRpcScalaJvm = idealinguaRuntimeRpcScala.jvm.remember
lazy val idealinguaRuntimeRpcScalaJs = idealinguaRuntimeRpcScala.js.remember

lazy val idealinguaTestDefs = inIdealingua.as.module.dependsOn(idealinguaRuntimeRpcScalaJvm)

lazy val idealinguaTranspilers = inIdealinguaX.as.cross(platforms)
  .settings(libraryDependencies += R.scala_xml)
  .settings(libraryDependencies ++= (R.scalameta +: R.circe).map(_.cross(platformDepsCrossVersion.value)))
  .depends(
    idealinguaCore,
    idealinguaRuntimeRpcScala,
  )
lazy val idealinguaTranspilersJvm = idealinguaTranspilers.jvm.remember
  .settings(ShadingSettings)
  .dependsSeq(Seq(
    idealinguaTestDefs,
    idealinguaRuntimeRpcTypescript,
    idealinguaRuntimeRpcGo,
    idealinguaRuntimeRpcCSharp,
  ).map(_.testOnlyRef))

lazy val idealinguaTranspilersJs = idealinguaTranspilers.js.remember
  .settings(libraryDependencies += C.jawn)

lazy val idealinguaRuntimeRpcHttp4s = inIdealingua.as.module
  .depends(idealinguaRuntimeRpcScalaJvm, logstageCore, logstageAdapterSlf4j)
  .dependsSeq(Seq(idealinguaTestDefs).map(_.testOnlyRef))
  .settings(libraryDependencies ++= R.http4s_all ++ R.java_websocket)

lazy val idealinguaRuntimeRpcTypescript = inIdealingua.as.module

lazy val idealinguaRuntimeRpcCSharp = inIdealingua.as.module

lazy val idealinguaRuntimeRpcGo = inIdealingua.as.module

lazy val idealinguaCompilerDeps = Seq[ProjectReferenceEx](
  idealinguaTranspilersJvm,
  idealinguaRuntimeRpcScalaJvm,
  idealinguaRuntimeRpcTypescript,
  idealinguaRuntimeRpcGo,
  idealinguaRuntimeRpcCSharp,
  idealinguaTestDefs,
)

lazy val idealinguaCompiler = inIdealinguaBase.as.module
  .depends(idealinguaCompilerDeps: _*)
  .settings(AppSettings)
  .enablePlugins(ScriptedPlugin)
  .settings(
    libraryDependencies ++= Seq(R.scopt, R.typesafe_config)
    , mainClass in assembly := Some("com.github.pshirshov.izumi.idealingua.compiler.CommandlineIDLCompiler")
  )
  .settings(addArtifact(artifact in(Compile, assembly), assembly))


lazy val sbtIzumi = inSbt.as
  .module

lazy val sbtIzumiDeps = inSbt.as
  .module
  .settings(withBuildInfo("com.github.pshirshov.izumi.sbt.deps", "Izumi"))

lazy val sbtIdealingua = inSbt.as
  .module
  .depends(idealinguaCompilerDeps: _*)

lazy val sbtTests = inSbt.as
  .module
  .depends(sbtIzumiDeps, sbtIzumi, sbtIdealingua)

lazy val logstage: Seq[ProjectReference] = Seq(
  logstageCore
  , logstageDi
  , logstageSinkSlf4j
  , logstageAdapterSlf4j
  , logstageRenderingCirce
  , logstageConfig
  , logstageConfigDi
)
lazy val distage: Seq[ProjectReference] = Seq(
  distageRoles
  , distageRolesScalaopt
  , distageCats
  , distageStatic
  , distageTestkit
)
lazy val idealingua: Seq[ProjectReference] = Seq(
  fastparseShaded,
  idealinguaModelJvm,
  idealinguaCoreJvm,
  idealinguaTranspilersJvm,
  idealinguaRuntimeRpcScalaJvm,
  idealinguaRuntimeRpcHttp4s,
  idealinguaCompiler,
)

lazy val fundamentalsJvm: Seq[ProjectReference] = Seq(
  fundamentalsFunctionalJvm,
  fundamentalsCollectionsJvm,
  fundamentalsPlatformJvm,
  fundamentalsBioJvm,
)

lazy val izsbt: Seq[ProjectReference] = Seq(
  sbtIzumi, sbtIdealingua, sbtTests, sbtIzumiDeps
)

lazy val idealinguaJs: Seq[ProjectReference] = Seq(
  idealinguaModelJs,
  idealinguaCoreJs,
  idealinguaRuntimeRpcScalaJs,
  idealinguaTranspilersJs,
)

lazy val fundamentalsJs: Seq[ProjectReference] = Seq(
  fundamentalsFunctionalJs,
  fundamentalsCollectionsJs,
  fundamentalsPlatformJs,
  fundamentalsBioJs,
)

lazy val allJsProjects = fundamentalsJs ++
  idealinguaJs

lazy val allProjects = fundamentalsJvm ++
  distage ++
  logstage ++
  idealingua ++
  izsbt ++
  Seq(microsite: ProjectReference)

lazy val unidocExcludes = izsbt ++ allJsProjects

lazy val microsite = inDoc.as.module
  .enablePlugins(ScalaUnidocPlugin, ParadoxSitePlugin, SitePlugin, GhpagesPlugin, ParadoxMaterialThemePlugin, PreprocessPlugin, TutPlugin)
  .settings(
    skip in publish := true
    , DocKeys.prefix := {
      if (isSnapshot.value) {
        "latest/snapshot"
      } else {
        "latest/release"
      }
    }
    , siteSubdirName in ScalaUnidoc := s"${DocKeys.prefix.value}/api"
    , siteSubdirName in Paradox := s"${DocKeys.prefix.value}/doc"
    , previewFixedPort := Some(9999)
    , git.remoteRepo := "git@github.com:7mind/izumi-microsite.git"
    , paradoxProperties ++= Map(
      "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value}/api/com/github/pshirshov/",
      "scaladoc.base_url" -> s"/${DocKeys.prefix.value}/api/",
      "izumi.version" -> version.value,
    )
    , sourceDirectory in Paradox := tutTargetDirectory.value
    , makeSite := makeSite.dependsOn(tut).value
    , version in Paradox := version.value
    , excludeFilter in ghpagesCleanSite :=
      new FileFilter {
        def accept(f: File): Boolean = {
          (f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("latest")) && !f.toPath.startsWith(ghpagesRepository.value.toPath.resolve(DocKeys.prefix.value))) ||
            (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath ||
            (ghpagesRepository.value / ".nojekyll").getCanonicalPath == f.getCanonicalPath ||
            (ghpagesRepository.value / "index.html").getCanonicalPath == f.getCanonicalPath ||
            (ghpagesRepository.value / "README.md").getCanonicalPath == f.getCanonicalPath ||
            f.toPath.startsWith((ghpagesRepository.value / "media").toPath) ||
            f.toPath.startsWith((ghpagesRepository.value / "v0.5.50-SNAPSHOT").toPath)
        }
      }
  )
  .settings(ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox))
  .settings(
    paradoxMaterialTheme in Paradox ~= {
      _.withCopyright("7mind.io")
        .withRepository(uri("https://github.com/pshirshov/izumi-r2"))
      //        .withColor("222", "434343")
    }
    , addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc)
    , unidocProjectFilter in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(unidocExcludes: _*)
  )


lazy val `izumi-r2` = inRoot.as
  .root
  .transitiveAggregateSeq(allProjects ++ allJsProjects)
