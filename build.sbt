import com.github.pshirshov.izumi.sbt.deps.IzumiDeps.{R, _}
import com.github.pshirshov.izumi.sbt.plugins.IzumiConvenienceTasksPlugin.Keys._
import com.github.pshirshov.izumi.sbt.plugins.optional.IzumiPublishingPlugin.Keys._
import sbt.Keys.{publishMavenStyle, sourceDirectory}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

enablePlugins(IzumiGitEnvironmentPlugin)
disablePlugins(AssemblyPlugin, ScriptedPlugin)

name := "izumi"
organization in ThisBuild := "io.7mind.izumi"
defaultStubPackage in ThisBuild := Some("com.github.pshirshov.izumi")
publishMavenStyle in ThisBuild := true
homepage in ThisBuild := Some(url("https://izumi.7mind.io"))
licenses in ThisBuild := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))
developers in ThisBuild := List(
  Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/7mind"), email = "team@7mind.io"),
)
scmInfo in ThisBuild := Some(ScmInfo(url("https://github.com/7mind/izumi"), "scm:git:https://github.com/7mind/izumi.git"))

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

val GlobalSettingsRoot = new DefaultGlobalSettingsGroup {
  override val id = SettingsGroupId("GlobalSettingsRoot")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    crossScalaVersions := Seq(
      V.scala_212,
      V.scala_213,
    ),
    sonatypeProfileName := "io.7mind",
    testOptions in Test += Tests.Argument("-oDF"),
    addCompilerPlugin(R.kind_projector),
    scalacOptions ++= Seq(
      s"-Xmacro-settings:product-version=${version.value}",
      s"-Xmacro-settings:product-group=${organization.value}",
      s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
      s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}",
      s"-Xmacro-settings:scala-version=${scalaVersion.value}",
      s"-Xmacro-settings:scalatest-version=${V.scalatest}",
    ),
  )
}

val GlobalSettings = new DefaultGlobalSettingsGroup {
  override val id = SettingsGroupId("GlobalSettings")

  override val settings: Seq[sbt.Setting[_]] = GlobalSettingsRoot.settings ++ Seq(
    scalaVersion := crossScalaVersions.value.head,
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
      libraryDependencies ++= R.essentials.map(_.cross(platformDepsCrossVersion.value)),
      libraryDependencies ++= T.essentials.map(_.cross(platformDepsCrossVersion.value)),
    )
  ).flatten
}

val SbtSettings = new SettingsGroup {
  override val id = SettingsGroupId("SbtSettings")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      target ~= { t => t.toPath.resolve("primary").toFile },
      libraryDependencies ++= Seq(
        "org.scala-sbt" % "sbt" % sbtVersion.value
      ),
      sbtPlugin := true
    )
  ).flatten
}

val Scala212OnlySettings = new SettingsGroup {
  override val id = SettingsGroupId("Scala212Only")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      crossScalaVersions := Seq(V.scala_212),
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
      },
      scriptedBufferLog := false,
    )
  ).flatten
}


val JSSettings = new SettingsGroup {
  override val id = SettingsGroupId("JSSettings")

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      coverageEnabled := false
    )
  ).flatten
}

// --------------------------------------------

lazy val inRoot = In(".")
  .settings(GlobalSettingsRoot)

lazy val inDoc = In("doc")
  .settings(GlobalSettings)

lazy val base = Seq(GlobalSettings, LibSettings, WithoutBadPlugins)

lazy val inFundamentals = In("fundamentals")
  .settingsSeq(base)

lazy val inSbt = In("sbt")
  .settings(GlobalSettings, WithoutBadPluginsSbt)
  .settings(SbtSettings, SbtScriptedSettings)
  .settings(Scala212OnlySettings)

lazy val inDiStage = In("distage")
  .settingsSeq(base)
  .settings(WithFundamentals)

lazy val inLogStage = In("logstage")
  .settingsSeq(base)
  .settings(WithFundamentals)
lazy val inLogStageX = In("logstage")
  .settingsSeq(base)
  .settings(WithFundamentalsX)

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


lazy val inIdealinguaV1Base = In("idealingua-v1")
  .settings(GlobalSettings, WithFundamentals)

lazy val inIdealinguaV1BaseX = In("idealingua-v1")
  .settings(GlobalSettings, WithFundamentalsX)

lazy val inIdealinguaV1 = inIdealinguaV1Base
  .settingsSeq(base)
  .settings(WithFundamentals)

lazy val inIdealinguaV1X = inIdealinguaV1BaseX
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
  .enablePlugins(ScalaJSBundlerPlugin)
  .settings(JSSettings)
  .settings(
    scalaJSModuleKind := ModuleKind.CommonJSModule,
    npmDependencies in Compile ++= Seq(
      "hash.js" -> "1.1.7"
    ),
  )

lazy val fundamentalsFunctional = inFundamentals.as.cross(platforms)
lazy val fundamentalsFunctionalJvm = fundamentalsFunctional.jvm.remember
lazy val fundamentalsFunctionalJs = fundamentalsFunctional.js.remember
  .settings(JSSettings)


lazy val fundamentalsBio = inFundamentals.as.cross(platforms)
  .dependsOn(fundamentalsFunctional)
  .settings(
    libraryDependencies ++= R.cats_all.map(_.cross(platformDepsCrossVersion.value) % Optional),
    libraryDependencies ++= Seq(R.zio_core).map(_.cross(platformDepsCrossVersion.value) % Optional),
  )
lazy val fundamentalsBioJvm = fundamentalsBio.jvm.remember
lazy val fundamentalsBioJs = fundamentalsBio.js.remember
  .settings(JSSettings)


lazy val WithFundamentals = new SettingsGroup {
  override val id = SettingsGroupId("WithFundamentals")

  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    fundamentalsCollectionsJvm,
    fundamentalsPlatformJvm,
    fundamentalsFunctionalJvm
  )
}

lazy val WithFundamentalsX = new SettingsGroup {
  override val id = SettingsGroupId("WithFundamentalsX")

  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    fundamentalsCollections,
    fundamentalsPlatform,
    fundamentalsFunctional
  )
}
// --------------------------------------------

lazy val fundamentalsTypesafeConfig = inFundamentals.as.module
  .depends(fundamentalsReflectionJvm)
  .settings(
    libraryDependencies ++= Seq(
      R.typesafe_config,
      R.scala_reflect % scalaVersion.value,
    )
  )

lazy val fundamentalsReflection = inFundamentals.as.cross(platforms)
  .depends(fundamentalsPlatform, fundamentalsFunctional)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value % Provided
    )
  )

lazy val fundamentalsReflectionJvm = fundamentalsReflection.jvm.remember
lazy val fundamentalsReflectionJs = fundamentalsReflection.js.remember
  .settings(JSSettings)

lazy val fundamentalsJsonCirce = inFundamentals.as.cross(platforms)
  .dependsOn(fundamentalsPlatform, fundamentalsFunctional)
  .settings(
    libraryDependencies ++= R.circe.map(_.cross(platformDepsCrossVersion.value))
  )
lazy val fundamentalsJsonCirceJvm = fundamentalsJsonCirce.jvm.remember
lazy val fundamentalsJsonCirceJs = fundamentalsJsonCirce.js.remember
  .settings(libraryDependencies += C.jawn)
  .settings(JSSettings)

//-----------------------------------------------------------------------------
lazy val distageModel = inDiStage.as.module
  .depends(
    fundamentalsReflectionJvm,
    fundamentalsBioJvm, // all deps there are optional
  )
  .settings(
    libraryDependencies ++= R.cats_all.map(_ % Optional),
    // FIXME: remove runtime scala-reflect
    libraryDependencies += R.scala_reflect % scalaVersion.value,
  )

lazy val distageProxyCglib = inDiStage.as.module
  .depends(distageModel)
  .settings(
    libraryDependencies += R.cglib_nodep
  )

lazy val distageCore = inDiStage.as.module
  .depends(fundamentalsFunctionalJvm, distageModel, distageProxyCglib)

lazy val distageConfig = inDiStage.as.module
  .depends(
    fundamentalsTypesafeConfig,
    distageModel,
    distageCore.testOnlyRef,
  )
  .settings(
    libraryDependencies += R.typesafe_config,
//    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
    fork in Test := true,
  )

lazy val distagePlugins = inDiStage.as.module
  .depends(
    distageModel,
    distageCore.testOnlyRef,
    distageConfig.testOnlyRef,
    logstageCoreJvm.testOnlyRef,
  )
  .settings(
    libraryDependencies ++= Seq(R.fast_classpath_scanner)
  )

lazy val distageRolesApi = inDiStage.as.module
  .depends(distageModel)

lazy val distageRoles = inDiStage.as.module
  .depends(
    distageRolesApi,
    distageCore,
    distagePlugins,
    distageConfig,
    logstageRenderingCirceJvm,
    logstageAdapterSlf4j,
    logstageDi,
  )
  .settings(
    libraryDependencies ++= R.cats_all.map(_ % Optional),
    libraryDependencies ++= Seq(R.zio_core).map(_ % Optional),

  )

lazy val distageStatic = inDiStage.as.module
  .depends(distageCore, distageRoles.testOnlyRef)
  .settings(
    libraryDependencies += R.shapeless
  )

lazy val distageTestkit = inDiStage.as.module
  .depends(distageCore, distagePlugins, distageConfig, distageRoles, logstageDi)
  .settings(
    libraryDependencies ++= Seq(R.scalatest),
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
  )
//-----------------------------------------------------------------------------

lazy val logstageApi = inLogStageX.as.cross(platforms)
  .depends(fundamentalsReflection)
  .settings(
    libraryDependencies ++= Seq(R.scala_java_time).map(_.cross(platformDepsCrossVersion.value)),
    libraryDependencies += R.scala_reflect % scalaVersion.value % Provided,
  )
lazy val logstageApiJvm = logstageApi.jvm.remember
lazy val logstageApiJs = logstageApi.js.remember
  .settings(JSSettings)

lazy val logstageCore = inLogStageX.as.cross(platforms)
  .depends(logstageApi.ets, fundamentalsBio)
  .settings(
    libraryDependencies += R.scala_reflect % scalaVersion.value % Provided,
    libraryDependencies ++= (Seq(
      R.zio_core % Optional,
      R.cats_core % Optional,
      T.zio_core,
    ) ++ T.cats_all).map(_.cross(platformDepsCrossVersion.value)),
  )
lazy val logstageCoreJvm = logstageCore.jvm.remember
lazy val logstageCoreJs = logstageCore.js.remember
  .settings(JSSettings)
  .settings(
    // FIXME: 2.13 Scala.js dies on inlining here
    //  https://github.com/scala/bug/issues/11663
    scalacOptions -= "-opt-inline-from:com.github.pshirshov.izumi.**"
  )

lazy val logstageRenderingCirce = inLogStageX.as.cross(platforms)
  .depends(logstageCore.ets, fundamentalsJsonCirce)
lazy val logstageRenderingCirceJvm = logstageRenderingCirce.jvm.remember
lazy val logstageRenderingCirceJs = logstageRenderingCirce.js.remember
  .settings(JSSettings)

lazy val logstageDi = inLogStage.as.module
  .depends(
    logstageCoreJvm,
    logstageConfig,
    distageConfig,
    distageModel,
    distageCore.testOnlyRef,
  )

lazy val logstageConfig = inLogStage.as.module
  .depends(fundamentalsTypesafeConfig, logstageCoreJvm)

lazy val logstageAdapterSlf4j = inLogStage.as.module
  .depends(logstageCoreJvm)
  .settings(
    libraryDependencies += R.slf4j_api,
    compileOrder in Compile := CompileOrder.Mixed,
    compileOrder in Test := CompileOrder.Mixed,
    classLoaderLayeringStrategy in Test := ClassLoaderLayeringStrategy.Flat,
  )

lazy val logstageSinkSlf4j = inLogStage.as.module
  .depends(
    logstageApiJvm,
    logstageCoreJvm.testOnlyRef
  )
  .settings(libraryDependencies ++= Seq(R.slf4j_api, T.slf4j_simple))


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
lazy val idealinguaV1Model = inIdealinguaV1X.as.cross(platforms)
lazy val idealinguaV1ModelJvm = idealinguaV1Model.jvm.remember
lazy val idealinguaV1ModelJs = idealinguaV1Model.js.remember
  .settings(JSSettings)


lazy val idealinguaV1Core = inIdealinguaV1X.as.cross(platforms)
  .settings(libraryDependencies ++= Seq(R.fastparse).map(_.cross(platformDepsCrossVersion.value)))
  .depends(idealinguaV1Model, fundamentalsReflection)
lazy val idealinguaV1CoreJvm = idealinguaV1Core.jvm.remember
lazy val idealinguaV1CoreJs = idealinguaV1Core.js.remember
  .settings(JSSettings)


lazy val idealinguaV1RuntimeRpcScala = inIdealinguaV1X.as.cross(platforms)
  .dependsOn(fundamentalsBio, fundamentalsJsonCirce)
  .settings(
    libraryDependencies += R.scala_reflect % scalaVersion.value % Provided,
    libraryDependencies ++= (Seq(
      R.zio_core,
      R.zio_interop,
    ) ++ R.cats_all).map(_.cross(platformDepsCrossVersion.value))
  )

lazy val idealinguaV1RuntimeRpcScalaJvm = idealinguaV1RuntimeRpcScala.jvm.remember
lazy val idealinguaV1RuntimeRpcScalaJs = idealinguaV1RuntimeRpcScala.js.remember
  .settings(JSSettings)


lazy val idealinguaV1TestDefs = inIdealinguaV1.as.module.dependsOn(idealinguaV1RuntimeRpcScalaJvm)


lazy val idealinguaV1Transpilers = inIdealinguaV1X.as.cross(platforms)
  .settings(libraryDependencies += R.scala_xml)
  .settings(libraryDependencies += R.scalameta)
  .depends(
    idealinguaV1Core,
    idealinguaV1RuntimeRpcScala,
    fundamentalsJsonCirce,
  )
lazy val idealinguaV1TranspilersJvm = idealinguaV1Transpilers.jvm.remember
  .settings(
    // FIXME: workaround for broken classpath on sbt 1.3.0-RC2 (ClassLoaderLayeringStrategy.Flat doesn't help)
    fork in Test := true,
  )
  .dependsSeq(Seq(
    idealinguaV1TestDefs,
    idealinguaV1RuntimeRpcTypescript,
    idealinguaV1RuntimeRpcGo,
    idealinguaV1RuntimeRpcCSharp,
  ).map(_.testOnlyRef))
lazy val idealinguaV1TranspilersJs = idealinguaV1Transpilers.js.remember
  .settings(JSSettings)


lazy val idealinguaV1RuntimeRpcHttp4s = inIdealinguaV1.as.module
  .depends(
    idealinguaV1RuntimeRpcScalaJvm,
    logstageCoreJvm,
    logstageAdapterSlf4j,
  )
  .dependsSeq(Seq(idealinguaV1TestDefs).map(_.testOnlyRef))
  .settings(
    libraryDependencies ++= R.http4s_all,
    libraryDependencies += R.asynchttpclient,
  )

lazy val idealinguaV1RuntimeRpcTypescript = inIdealinguaV1.as.module

lazy val idealinguaV1RuntimeRpcCSharp = inIdealinguaV1.as.module

lazy val idealinguaV1RuntimeRpcGo = inIdealinguaV1.as.module

lazy val idealinguaV1CompilerDeps = Seq[ProjectReferenceEx](
  idealinguaV1TranspilersJvm,
  idealinguaV1RuntimeRpcScalaJvm,
  idealinguaV1RuntimeRpcTypescript,
  idealinguaV1RuntimeRpcGo,
  idealinguaV1RuntimeRpcCSharp,
  idealinguaV1TestDefs,
)

lazy val idealinguaV1Compiler = inIdealinguaV1Base.as.module
  .depends(idealinguaV1CompilerDeps: _*)
  .settings(AppSettings)
  .settings(
    libraryDependencies ++= Seq(R.typesafe_config),
    mainClass in assembly := Some("com.github.pshirshov.izumi.idealingua.compiler.CommandlineIDLCompiler"),
    // FIXME: workaround for https://github.com/zio/interop-cats/issues/16
    assemblyMergeStrategy in assembly := {
      case path if path.contains("zio/BuildInfo$.class") =>
        MergeStrategy.last
      case p =>
        (assemblyMergeStrategy in assembly).value(p)
    }
  )
  .settings(
    artifact in(Compile, assembly) := {
      val art = (artifact in(Compile, assembly)).value
      art.withClassifier(Some("assembly"))
    },

    addArtifact(artifact in(Compile, assembly), assembly)
  )
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

lazy val sbtIzumi = inSbt.as
  .module

lazy val sbtIzumiDeps = inSbt.as
  .module
  .settings(withBuildInfo("com.github.pshirshov.izumi.sbt.deps", "Izumi"))


lazy val sbtTests = inSbt.as
  .module
  .depends(sbtIzumiDeps, sbtIzumi)

lazy val logstage: Seq[ProjectReference] = Seq(
  logstageApiJvm,
  logstageCoreJvm,
  logstageRenderingCirceJvm,
  logstageDi,
  logstageSinkSlf4j,
  logstageAdapterSlf4j,
  logstageConfig
)

lazy val logstageJs: Seq[ProjectReference] = Seq(
  logstageApiJs,
  logstageCoreJs,
  logstageRenderingCirceJs,
)

lazy val distage: Seq[ProjectReference] = Seq(
  distageRoles,
  distageStatic,
  distageTestkit
)

lazy val idealinguaV1: Seq[ProjectReference] = Seq(
  idealinguaV1ModelJvm,
  idealinguaV1CoreJvm,
  idealinguaV1TranspilersJvm,
  idealinguaV1RuntimeRpcScalaJvm,
  idealinguaV1RuntimeRpcHttp4s,
  idealinguaV1Compiler,
  idealinguaV1TestDefs,
)

lazy val fundamentalsJvm: Seq[ProjectReference] = Seq(
  fundamentalsFunctionalJvm,
  fundamentalsCollectionsJvm,
  fundamentalsPlatformJvm,
  fundamentalsBioJvm,
  fundamentalsJsonCirceJvm,
)

lazy val izsbt: Seq[ProjectReference] = Seq(
  sbtIzumi, sbtTests, sbtIzumiDeps
)

lazy val idealinguaV1Js: Seq[ProjectReference] = Seq(
  idealinguaV1ModelJs,
  idealinguaV1CoreJs,
  idealinguaV1RuntimeRpcScalaJs,
  idealinguaV1TranspilersJs,
)

lazy val fundamentalsJs: Seq[ProjectReference] = Seq(
  fundamentalsFunctionalJs,
  fundamentalsCollectionsJs,
  fundamentalsPlatformJs,
  fundamentalsBioJs,
  fundamentalsJsonCirceJs,
  fundamentalsReflectionJs,
)

lazy val allJsProjects = fundamentalsJs ++
  logstageJs ++
  idealinguaV1Js

lazy val allProjects = fundamentalsJvm ++
  distage ++
  logstage ++
  idealinguaV1 ++
  izsbt ++
  Seq(microsite: ProjectReference)

lazy val unidocExcludes = izsbt ++ allJsProjects ++ idealinguaV1

lazy val allProjectsExceptMicrosite = distage ++ logstage ++ idealinguaV1 ++ izsbt

lazy val microsite = inDoc.as.module
  .dependsOn(allProjectsExceptMicrosite.map(x => x: ClasspathDep[ProjectReference]): _*)
  .enablePlugins(
    ScalaUnidocPlugin,
    ParadoxSitePlugin,
    SitePlugin,
    GhpagesPlugin,
    ParadoxMaterialThemePlugin,
    PreprocessPlugin,
    MdocPlugin
  )
  .settings(Scala212OnlySettings)
  .settings(ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox))
  .settings(
    skip in publish := true,
    DocKeys.prefix := {
      if (isSnapshot.value) {
        "latest/snapshot"
      } else {
        "latest/release"
      }
    },
    siteSubdirName in ScalaUnidoc := s"${DocKeys.prefix.value}/api",
    siteSubdirName in Paradox := s"${DocKeys.prefix.value}/doc",
    previewFixedPort := Some(9999),
    git.remoteRepo := "git@github.com:7mind/izumi-microsite.git",
    paradoxProperties ++= Map(
      "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value}/api/com/github/pshirshov/",
      "scaladoc.base_url" -> s"/${DocKeys.prefix.value}/api/",
      "izumi.version" -> version.value,
    ),
    classLoaderLayeringStrategy in Compile := ClassLoaderLayeringStrategy.Flat,
    mdocIn := baseDirectory.value / "src/main/tut",
    sourceDirectory in Paradox := mdocOut.value,
    mdocExtraArguments ++= Seq(" --no-link-hygiene"),
    (mappings in SitePlugin.autoImport.makeSite) := {
      (mappings in SitePlugin.autoImport.makeSite)
        .dependsOn(mdoc.toTask(" "))
        .value
    },
    version in Paradox := version.value,
    excludeFilter in ghpagesCleanSite :=
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
  .settings(
    paradoxMaterialTheme in Paradox ~= {
      _.withCopyright("7mind.io")
        .withRepository(uri("https://github.com/7mind/izumi"))
      //        .withColor("222", "434343")
    },
    addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    unidocProjectFilter in(ScalaUnidoc, unidoc) := inAnyProject -- inProjects(unidocExcludes: _*)
  )
  .settings(skip in publish := true)

lazy val `izumi-r2`: Project = inRoot.as
  .root
  .transitiveAggregateSeq(allProjects ++ allJsProjects)
  .settings(skip in publish := true)
  .settings(crossScalaVersions := Nil)
  .settings(scalaVersion := V.scala_212)
