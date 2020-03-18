import $ivy.`io.7mind.izumi.sbt::sbtgen:0.0.52`
import izumi.sbtgen._
import izumi.sbtgen.model._

object Izumi {

  object V {
    val collection_compat = Version.VExpr("V.collection_compat")
    val kind_projector = Version.VExpr("V.kind_projector")
    val silencer = Version.VExpr("V.silencer")
    val scalatest = Version.VExpr("V.scalatest")
    val cats = Version.VExpr("V.cats")
    val cats_effect = Version.VExpr("V.cats_effect")
    val zio = Version.VExpr("V.zio")
    val zio_interop_cats = Version.VExpr("V.zio_interop_cats")
    val circe = Version.VExpr("V.circe")
    val circe_generic_extras = Version.VExpr("V.circe_generic_extras")
    val circe_derivation = Version.VExpr("V.circe_derivation")
    val pureconfig = Version.VExpr("V.pureconfig")
    val magnolia = Version.VExpr("V.magnolia")
    val jawn = Version.VExpr("V.jawn")
    val http4s = Version.VExpr("V.http4s")
    val classgraph = Version.VExpr("V.classgraph")
    val slf4j = Version.VExpr("V.slf4j")
    val typesafe_config = Version.VExpr("V.typesafe_config")
    val cglib_nodep = Version.VExpr("V.cglib_nodep")
    val scala_java_time = Version.VExpr("V.scala_java_time")
    val scalamock = Version.VExpr("V.scalamock")
    val docker_java = Version.VExpr("V.docker_java")
  }

  object PV {
    val sbt_mdoc = Version.VExpr("PV.sbt_mdoc")
    val sbt_paradox_material_theme = Version.VExpr("PV.sbt_paradox_material_theme")
    val sbt_ghpages = Version.VExpr("PV.sbt_ghpages")
    val sbt_site = Version.VExpr("PV.sbt_site")
    val sbt_unidoc = Version.VExpr("PV.sbt_unidoc")
    val sbt_scoverage = Version.VExpr("PV.sbt_scoverage")
    val sbt_pgp = Version.VExpr("PV.sbt_pgp")
    val sbt_assembly = Version.VExpr("PV.sbt_assembly")

    val scala_js_version = Version.VExpr("PV.scala_js_version")
    val scala_native_version = Version.VExpr("PV.scala_native_version")
    val crossproject_version = Version.VExpr("PV.crossproject_version")
    val scalajs_bundler_version = Version.VExpr("PV.scalajs_bundler_version")
  }

  def entrypoint(args: Seq[String]) = {
    Entrypoint.main(izumi, settings, Seq("-o", ".") ++ args)
  }

  val settings = GlobalSettings(
    groupId = "io.7mind.izumi",
    sbtVersion = None,
    scalaJsVersion = PV.scala_js_version,
    scalaNativeVersion = PV.scala_native_version,
    crossProjectVersion = PV.crossproject_version,
    bundlerVersion = PV.scalajs_bundler_version,
  )

  object Deps {
    final val collection_compat = Library("org.scala-lang.modules", "scala-collection-compat", V.collection_compat, LibraryType.Auto)
    final val scalatest = Library("org.scalatest", "scalatest", V.scalatest, LibraryType.Auto) in Scope.Test.all

    final val cats_core = Library("org.typelevel", "cats-core", V.cats, LibraryType.Auto)
    final val cats_effect = Library("org.typelevel", "cats-effect", V.cats_effect, LibraryType.Auto)
    final val cats_all = Seq(
      cats_core,
      cats_effect,
    )

    final val circe_core = Library("io.circe", "circe-core", V.circe, LibraryType.Auto)
    final val circe_derivation = Library("io.circe", "circe-derivation", V.circe_derivation, LibraryType.Auto)
    final val circe = Seq(
      circe_core,
      Library("io.circe", "circe-parser", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-literal", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-generic-extras", V.circe_generic_extras, LibraryType.Auto),
      circe_derivation,
    ).map(_ in Scope.Compile.all)
    final val pureconfig_magnolia = Library("com.github.pureconfig", "pureconfig-magnolia", V.pureconfig, LibraryType.Auto)
    final val magnolia = Library("com.propensive", "magnolia", V.magnolia, LibraryType.Auto)

    final val zio_core = Library("dev.zio", "zio", V.zio, LibraryType.Auto)
    final val zio_interop_cats = Library("dev.zio", "zio-interop-cats", V.zio_interop_cats, LibraryType.Auto)
    final val zio_all = Seq(
      zio_core,
      zio_interop_cats,
    )

    final val typesafe_config = Library("com.typesafe", "config", V.typesafe_config, LibraryType.Invariant) in Scope.Compile.all
    final val jawn = Library("org.typelevel", "jawn-parser", V.jawn, LibraryType.AutoJvm)

    final val scala_sbt = Library("org.scala-sbt", "sbt", Version.VExpr("sbtVersion.value"), LibraryType.Invariant)
    final val scala_compiler = Library("org.scala-lang", "scala-compiler", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_library = Library("org.scala-lang", "scala-library", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_reflect = Library("org.scala-lang", "scala-reflect", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)

    final val cglib_nodep = Library("cglib", "cglib-nodep", V.cglib_nodep, LibraryType.Invariant) in Scope.Compile.jvm

    final val projector = Library("org.typelevel", "kind-projector", V.kind_projector, LibraryType.Invariant)
      .more(LibSetting.Raw("cross CrossVersion.full"))
    final val silencer_plugin = Library("com.github.ghik", "silencer-plugin", V.silencer, LibraryType.Invariant)
      .more(LibSetting.Raw("cross CrossVersion.full"))
    final val silencer_lib = Library("com.github.ghik", "silencer-lib", V.silencer, LibraryType.Invariant)
      .more(LibSetting.Raw("cross CrossVersion.full"))

    final val fast_classpath_scanner = Library("io.github.classgraph", "classgraph", V.classgraph, LibraryType.Invariant) in Scope.Compile.jvm
    final val scala_java_time = Library("io.github.cquiroz", "scala-java-time", V.scala_java_time, LibraryType.Auto)
    final val scalamock = Library("org.scalamock", "scalamock", V.scalamock, LibraryType.Auto)

    final val slf4j_api = Library("org.slf4j", "slf4j-api", V.slf4j, LibraryType.Invariant) in Scope.Compile.jvm
    final val slf4j_simple = Library("org.slf4j", "slf4j-simple", V.slf4j, LibraryType.Invariant) in Scope.Test.jvm

    final val http4s_client = Seq(
      Library("org.http4s", "http4s-blaze-client", V.http4s, LibraryType.Auto),
    )

    val http4s_server = Seq(
      Library("org.http4s", "http4s-dsl", V.http4s, LibraryType.Auto),
      Library("org.http4s", "http4s-circe", V.http4s, LibraryType.Auto),
      Library("org.http4s", "http4s-blaze-server", V.http4s, LibraryType.Auto),
    )

    val http4s_all = http4s_server ++ http4s_client

    val docker_java = Library("com.github.docker-java", "docker-java", V.docker_java, LibraryType.Invariant)
  }

  import Deps._

  // DON'T REMOVE, these variables are read from CI build (build.sh)
  final val scala211 = ScalaVersion("2.11.12")
  final val scala212 = ScalaVersion("2.12.10")
  final val scala213 = ScalaVersion("2.13.1")

  object Groups {
    final val fundamentals = Set(Group("fundamentals"))
    final val distage = Set(Group("distage"))
    final val logstage = Set(Group("logstage"))
    final val docs = Set(Group("docs"))
    final val sbt = Set(Group("sbt"))
  }

  object Targets {
    // switch order to use 2.13 in IDEA
    val targetScala = Seq(scala212, scala213)
//    val targetScala = Seq(scala213, scala212)
    val targetScalaWith211 = Seq(scala212, scala213, scala211)
    private val jvmPlatform = PlatformEnv(
      platform = Platform.Jvm,
      language = targetScala,
    )
    private val jvmPlatformWith211 = PlatformEnv(
      platform = Platform.Jvm,
      language = targetScalaWith211,
    )
    private val jsPlatform = PlatformEnv(
      platform = Platform.Js,
      language = targetScala,
      settings = Seq(
        "coverageEnabled" := false,
        "scalaJSLinkerConfig" in (SettingScope.Project, Platform.Js) := "{ scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }".raw,
      ),
    )
    private val jsPlatformWith211 = PlatformEnv(
      platform = Platform.Js,
      language = targetScalaWith211,
      settings = Seq(
        "coverageEnabled" := false,
        "scalaJSLinkerConfig" in (SettingScope.Project, Platform.Js) := "{ scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }".raw,
      ),
    )
    private val nativePlatform = PlatformEnv(
      platform = Platform.Native,
      language = Seq(scala211),
      settings = Seq(
        "coverageEnabled" := false,
        "test" in Platform.Native := "{}".raw,
        "test" in(SettingScope.Test, Platform.Native) := "{}".raw,
      ),
    )
    private val jvmPlatformSbt = PlatformEnv(
      platform = Platform.Jvm,
      language = Seq(scala212),
      settings = Seq(
        "coverageEnabled" := false,
      ),
    )
    final val cross = Seq(jvmPlatform, jsPlatform)
    final val crossNativeWith211 = Seq(jvmPlatformWith211, jsPlatformWith211, nativePlatform)
    final val jvm = Seq(jvmPlatform)
    final val js = Seq(jsPlatform)
    final val jvmSbt = Seq(jvmPlatformSbt)
  }

  final val assemblyPluginJvm = Plugin("AssemblyPlugin", Platform.Jvm)
  final val assemblyPluginJs = Plugin("AssemblyPlugin", Platform.Js)

  object Projects {

    final val plugins = Plugins(
      Seq.empty,
      Seq(assemblyPluginJs, assemblyPluginJvm),
    )

    object root {
      final val id = ArtifactId("izumi")
      final val plugins = Plugins(
        enabled = Seq(Plugin("SbtgenVerificationPlugin")),
        disabled = Seq(Plugin("AssemblyPlugin")),
      )
      final val settings = Seq()

      final val sharedAggSettings = Seq(
        "crossScalaVersions" := Targets.targetScala.map(_.value),
        "scalaVersion" := "crossScalaVersions.value.head".raw,
      )

      final val rootSettings = Defaults.SharedOptions ++ Seq(
        "crossScalaVersions" := "Nil".raw,
        "scalaVersion" := Targets.targetScala.head.value,
        "organization" in SettingScope.Build := "io.7mind.izumi",
        "sonatypeProfileName" := "io.7mind",
        "sonatypeSessionName" := """s"[sbt-sonatype] ${name.value} ${version.value} ${java.util.UUID.randomUUID}"""".raw,
        "publishTo" in SettingScope.Build :=
          """
            |(if (!isSnapshot.value) {
            |    sonatypePublishToBundle.value
            |  } else {
            |    Some(Opts.resolver.sonatypeSnapshots)
            |})
            |""".stripMargin.raw,
        "credentials" in SettingScope.Build += """Credentials(file(".secrets/credentials.sonatype-nexus.properties"))""".raw,
        "homepage" in SettingScope.Build := """Some(url("https://izumi.7mind.io"))""".raw,
        "licenses" in SettingScope.Build := """Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))""".raw,
        "developers" in SettingScope.Build :=
          """List(
          Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/7mind"), email = "team@7mind.io"),
        )""".raw,
        "scmInfo" in SettingScope.Build := """Some(ScmInfo(url("https://github.com/7mind/izumi"), "scm:git:https://github.com/7mind/izumi.git"))""".raw,
        "scalacOptions" in SettingScope.Build += s"""${"\"" * 3}-Xmacro-settings:scalatest-version=${V.scalatest}${"\"" * 3}""".raw,
      )

      final val sharedSettings = Defaults.SbtMeta ++ Seq(
        "testOptions" in SettingScope.Test += """Tests.Argument("-oDF")""".raw,
        "scalacOptions" ++= Seq(
          SettingKey(Some(scala212), None) := Defaults.Scala212Options,
          SettingKey(Some(scala213), None) := Defaults.Scala213Options,
          SettingKey.Default := Const.EmptySeq,
        ),
        "scalacOptions" ++= Seq(
          SettingKey(Some(scala212), Some(true)) := Seq(
            "-opt:l:inline",
            "-opt-inline-from:izumi.**",
          ),
          SettingKey(Some(scala213), Some(true)) := Seq(
            "-opt:l:inline",
            "-opt-inline-from:izumi.**",
          ),
          SettingKey.Default := Const.EmptySeq
        ),
      )

    }

    object fundamentals {
      final val id = ArtifactId("fundamentals")
      final val basePath = Seq("fundamentals")

      final val collections = ArtifactId("fundamentals-collections")
      final val platform = ArtifactId("fundamentals-platform")
      final val functional = ArtifactId("fundamentals-functional")
      final val bio = ArtifactId("fundamentals-bio")

      final val typesafeConfig = ArtifactId("fundamentals-typesafe-config")
      final val reflection = ArtifactId("fundamentals-reflection")
      final val fundamentalsJsonCirce = ArtifactId("fundamentals-json-circe")

      final val thirdpartyBoopickleShaded = ArtifactId("fundamentals-thirdparty-boopickle-shaded")

      final lazy val basics = Seq(
        platform,
        collections,
        functional,
      ).map(_ in Scope.Runtime.all)
    }

    object distage {
      final val id = ArtifactId("distage")
      final val basePath = Seq("distage")

      final lazy val model = ArtifactId("distage-core-api")
      final lazy val proxyCglib = ArtifactId("distage-core-proxy-cglib")
      final lazy val core = ArtifactId("distage-core")
      final lazy val config = ArtifactId("distage-extension-config")
      final lazy val plugins = ArtifactId("distage-extension-plugins")
      final lazy val docker = ArtifactId("distage-framework-docker")
      final lazy val frameworkApi = ArtifactId("distage-framework-api")
      final lazy val framework = ArtifactId("distage-framework")
      final lazy val testkitCore = ArtifactId("distage-testkit-core")
      final lazy val testkitScalatest = ArtifactId("distage-testkit-scalatest")
      final lazy val logging = ArtifactId("distage-extension-logstage")
    }

    object logstage {
      final val id = ArtifactId("logstage")
      final val basePath = Seq("logstage")

      final lazy val core = ArtifactId("logstage-core")
      final lazy val renderingCirce = ArtifactId("logstage-rendering-circe")
      final lazy val adapterSlf4j = ArtifactId("logstage-adapter-slf4j")
      final lazy val sinkSlf4j = ArtifactId("logstage-sink-slf4j")
    }

    object docs {
      final val id = ArtifactId("doc")
      final val basePath = Seq("doc")

      final lazy val microsite = ArtifactId("microsite")
    }

    object sbtplugins {
      final val id = ArtifactId("sbt-plugins")
      final val basePath = Seq("sbt-plugins")

      final val settings = Seq(
        "sbtPlugin" := true,
      )

      final lazy val izumi_deps = ArtifactId("sbt-izumi-deps")
    }

  }

  final val forkTests = Seq(
    "fork" in(SettingScope.Test, Platform.Jvm) := true,
  )

  final val crossScalaSources = Seq(
    "unmanagedSourceDirectories" in SettingScope.Compile :=
      """(unmanagedSourceDirectories in Compile).value.flatMap {
        |  dir =>
        |   Seq(dir, file(dir.getPath + (CrossVersion.partialVersion(scalaVersion.value) match {
        |     case Some((2, 11)) => "_2.11"
        |     case Some((2, 12)) => "_2.12"
        |     case Some((2, 13)) => "_2.13"
        |     case _             => "_3.0"
        |   })))
        |}""".stripMargin.raw,
  )

  final lazy val fundamentals = Aggregate(
    name = Projects.fundamentals.id,
    artifacts = Seq(
      Artifact(
        name = Projects.fundamentals.collections,
        libs = Seq.empty,
        depends = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.platform,
        libs = Seq(
          scala_reflect in Scope.Provided.all,
        ),
        depends = Seq(
          Projects.fundamentals.collections in Scope.Compile.all
        ),
        settings = Seq(
          "npmDependencies" in(SettingScope.Test, Platform.Js) ++= Seq("hash.js" -> "1.1.7"),
        ) ++ crossScalaSources,
        plugins = Plugins(Seq(Plugin("ScalaJSBundlerPlugin", Platform.Js))),
      ),
      Artifact(
        name = Projects.fundamentals.functional,
        libs = Seq.empty,
        depends = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.thirdpartyBoopickleShaded,
        libs = Seq(scala_reflect in Scope.Provided.all),
        depends = Seq.empty,
        settings = crossScalaSources ++ Seq(
          SettingDef.RawSettingDef(
            """scalacOptions in Compile --= Seq("-Ywarn-value-discard","-Ywarn-unused:_", "-Wvalue-discard", "-Wunused:_")""",
            FullSettingScope(SettingScope.Compile, Platform.All),
          ),
        ),
      ),
      Artifact(
        name = Projects.fundamentals.reflection,
        libs = Seq(scala_reflect in Scope.Provided.all),
        depends = Seq(
          Projects.fundamentals.platform,
          Projects.fundamentals.functional,
          Projects.fundamentals.thirdpartyBoopickleShaded,
        ),
      ),
      Artifact(
        name = Projects.fundamentals.bio,
        libs = allMonadsOptional,
        depends = Seq.empty,
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.fundamentals.fundamentalsJsonCirce,
        libs = circe ++ Seq(
          jawn in Scope.Compile.js,
          scala_reflect in Scope.Provided.all,
        ),
        depends = Projects.fundamentals.basics,
        platforms = Targets.cross,
      ),
    ),
    pathPrefix = Projects.fundamentals.basePath,
    groups = Groups.fundamentals,
    defaultPlatforms = Targets.crossNativeWith211,
    enableProjectSharedAggSettings = false,
    settings = Seq(
      "crossScalaVersions" := "Nil".raw,
    )
  )

  final val allMonadsOptional = (cats_all ++ Seq(zio_core)).map(_ in Scope.Optional.all)
  final val allMonadsTest = (cats_all ++ Seq(zio_core)).map(_ in Scope.Test.all)

  final lazy val distage = Aggregate(
    name = Projects.distage.id,
    artifacts = Seq(
      Artifact(
        name = Projects.distage.model,
        libs = allMonadsOptional ++ Seq(scala_reflect in Scope.Provided.all),
        depends = Projects.fundamentals.basics ++ Seq(Projects.fundamentals.bio, Projects.fundamentals.reflection).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.proxyCglib,
        libs = Seq(cglib_nodep),
        depends = Seq(Projects.distage.model).map(_ in Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.core,
        libs = allMonadsTest ++ Seq(
          scala_java_time in Scope.Test.js,
        ),
        depends = Seq(Projects.distage.model in Scope.Compile.all, Projects.distage.proxyCglib in Scope.Compile.jvm),
      ),
      Artifact(
        name = Projects.distage.config,
        libs = Seq(pureconfig_magnolia, magnolia).map(_ in Scope.Compile.all) ++ Seq(scala_reflect in Scope.Provided.all),
        depends = Seq(Projects.distage.model).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.plugins,
        libs = Seq(fast_classpath_scanner),
        depends = Seq(Projects.distage.model).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ tin Scope.Test.all) ++
          Seq(Projects.distage.config, Projects.logstage.core).map(_ in Scope.Test.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.logging,
        libs = Seq.empty,
        depends = Seq(Projects.distage.config, Projects.distage.model).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all) ++
          Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.frameworkApi,
        libs = Seq.empty,
        depends = Seq(Projects.distage.model).map(_ in Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.framework,
        libs = allMonadsOptional ++ Seq(scala_reflect in Scope.Provided.all),
        depends = Seq(Projects.distage.logging, Projects.logstage.renderingCirce).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.frameworkApi, Projects.distage.plugins, Projects.distage.config).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.docker,
        libs = allMonadsTest ++ Seq(docker_java in Scope.Compile.jvm),
        depends = Seq(Projects.distage.core, Projects.distage.config, Projects.distage.frameworkApi, Projects.distage.logging).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.testkitScalatest in Scope.Test.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.testkitCore,
        libs = allMonadsOptional,
        depends =
          Seq(Projects.distage.config, Projects.distage.framework, Projects.distage.logging).map(_ in Scope.Compile.all) ++
            Seq(Projects.distage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.testkitScalatest,
        libs = allMonadsOptional ++ Seq(
          scalamock in Scope.Test.all,
          scalatest.dependency in Scope.Compile.all,
        ),
        depends =
          Seq(Projects.distage.testkitCore).map(_ in Scope.Compile.all) ++
            Seq(Projects.distage.core, Projects.distage.plugins).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
      ),
    ),
    pathPrefix = Projects.distage.basePath,
    defaultPlatforms = Targets.cross,
    groups = Groups.distage,
  )

  final lazy val logstage = Aggregate(
    name = Projects.logstage.id,
    artifacts = Seq(
      Artifact(
        name = Projects.logstage.core,
        libs = Seq(scala_reflect in Scope.Provided.all) ++
          allMonadsOptional ++
          Seq(scala_java_time in Scope.Compile.js),
        depends = Seq(Projects.fundamentals.bio, Projects.fundamentals.reflection).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.logstage.renderingCirce,
        libs = Seq.empty,
        depends = Seq(Projects.fundamentals.fundamentalsJsonCirce).map(_ in Scope.Compile.all) ++ Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        name = Projects.logstage.adapterSlf4j,
        libs = Seq(slf4j_api),
        depends = Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
        settings = Seq(
          "compileOrder" in SettingScope.Compile := "CompileOrder.Mixed".raw,
          "compileOrder" in SettingScope.Test := "CompileOrder.Mixed".raw,
          "classLoaderLayeringStrategy" in SettingScope.Test := "ClassLoaderLayeringStrategy.Flat".raw,
        )
      ),
      Artifact(
        name = Projects.logstage.sinkSlf4j,
        libs = Seq(slf4j_api, slf4j_simple),
        depends = Seq(Projects.logstage.core).map(_ in Scope.Compile.all) ++ Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
      )
    ),
    pathPrefix = Projects.logstage.basePath,
    groups = Groups.logstage,
    defaultPlatforms = Targets.cross,
  )

  val all = Seq(fundamentals, distage, logstage)

  final lazy val docs = Aggregate(
    name = Projects.docs.id,
    artifacts = Seq(
      Artifact(
        name = Projects.docs.microsite,
        libs = (cats_all ++ zio_all ++ http4s_all).map(_ in Scope.Compile.all),
        depends = all.flatMap(_.artifacts).map(_.name in Scope.Compile.all).distinct,
        settings = Seq(
          "coverageEnabled" := false,
          "skip" in SettingScope.Raw("publish") := true,
          "DocKeys.prefix" :=
            """{if (isSnapshot.value) {
            "latest/snapshot"
          } else {
            "latest/release"
          }}""".raw,
          "previewFixedPort" := "Some(9999)".raw,
          "git.remoteRepo" := "git@github.com:7mind/izumi-microsite.git",
          "classLoaderLayeringStrategy" in SettingScope.Raw("Compile") := "ClassLoaderLayeringStrategy.Flat".raw,
          "mdocIn" := """baseDirectory.value / "src/main/tut"""".raw,
          "sourceDirectory" in SettingScope.Raw("Paradox") := "mdocOut.value".raw,
          "mdocExtraArguments" ++= Seq(" --no-link-hygiene"),
          "mappings" in SettingScope.Raw("SitePlugin.autoImport.makeSite") :=
            """{
            (mappings in SitePlugin.autoImport.makeSite)
              .dependsOn(mdoc.toTask(" "))
              .value
          }""".raw,
          "version" in SettingScope.Raw("Paradox") := "version.value".raw,
          SettingDef.RawSettingDef("ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox)"),
          SettingDef.RawSettingDef("addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc)"),
          SettingDef.RawSettingDef(
            "unidocProjectFilter in(ScalaUnidoc, unidoc) := inAggregates(`fundamentals-jvm`, transitive = true) || inAggregates(`distage-jvm`, transitive = true) || inAggregates(`logstage-jvm`, transitive = true)"
          ),
          SettingDef.RawSettingDef(
            """paradoxMaterialTheme in Paradox ~= {
            _.withCopyright("7mind.io")
              .withRepository(uri("https://github.com/7mind/izumi"))
            //        .withColor("222", "434343")
          }"""),
          "siteSubdirName" in SettingScope.Raw("ScalaUnidoc") := """s"${DocKeys.prefix.value}/api"""".raw,
          "siteSubdirName" in SettingScope.Raw("Paradox") := """s"${DocKeys.prefix.value}/doc"""".raw,
          SettingDef.RawSettingDef(
            """paradoxProperties ++= Map(
            "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value}/api/",
            "scaladoc.base_url" -> s"/${DocKeys.prefix.value}/api/",
            "izumi.version" -> version.value,
          )"""),
          SettingDef.RawSettingDef(
            """excludeFilter in ghpagesCleanSite :=
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
            }"""
          )
        ),
        plugins = Plugins(
          enabled = Seq(
            Plugin("ScalaUnidocPlugin"),
            Plugin("ParadoxSitePlugin"),
            Plugin("SitePlugin"),
            Plugin("GhpagesPlugin"),
            Plugin("ParadoxMaterialThemePlugin"),
            Plugin("PreprocessPlugin"),
            Plugin("MdocPlugin")
          ),
          disabled = Seq(Plugin("ScoverageSbtPlugin"))
        )
      ),
    ),
    pathPrefix = Projects.docs.basePath,
    groups = Groups.docs,
    defaultPlatforms = Targets.jvm,
    dontIncludeInSuperAgg = true,
  )

  final lazy val sbtplugins = Aggregate(
    name = Projects.sbtplugins.id,
    artifacts = Seq(
      Artifact(
        name = Projects.sbtplugins.izumi_deps,
        libs = Seq.empty,
        depends = Seq.empty,
        settings = Projects.sbtplugins.settings ++ Seq(
          SettingDef.RawSettingDef("""withBuildInfo("izumi.sbt.deps", "Izumi")""")
        ),
        plugins = Plugins(
          enabled = Seq.empty,
          disabled = Seq(Plugin("ScoverageSbtPlugin")),
        )
      ),
    ),
    pathPrefix = Projects.sbtplugins.basePath,
    groups = Groups.sbt,
    defaultPlatforms = Targets.jvmSbt,
    enableProjectSharedAggSettings = false,
    dontIncludeInSuperAgg = false,
  )

  val izumi: Project = Project(
    name = Projects.root.id,
    aggregates = Seq(
      fundamentals,
      distage,
      logstage,
      docs,
      sbtplugins,
    ),
    topLevelSettings = Projects.root.settings,
    sharedSettings = Projects.root.sharedSettings,
    sharedAggSettings = Projects.root.sharedAggSettings,
    rootSettings = Projects.root.rootSettings,
    imports = Seq.empty,
    globalLibs = Seq(
      ScopedLibrary(projector, FullDependencyScope(Scope.Compile, Platform.All), compilerPlugin = true),
      ScopedLibrary(silencer_plugin, FullDependencyScope(Scope.Compile, Platform.All), compilerPlugin = true),
      silencer_lib in Scope.Provided.all,
      collection_compat in Scope.Compile.all,
      scalatest,
    ),
    rootPlugins = Projects.root.plugins,
    globalPlugins = Projects.plugins,
    pluginConflictRules = Map(assemblyPluginJvm.name -> true),
    appendPlugins = Defaults.SbtGenPlugins ++ Seq(
      SbtPlugin("com.eed3si9n", "sbt-assembly", PV.sbt_assembly),
      SbtPlugin("com.jsuereth", "sbt-pgp", PV.sbt_pgp),
      SbtPlugin("org.scoverage", "sbt-scoverage", PV.sbt_scoverage),
      SbtPlugin("com.eed3si9n", "sbt-unidoc", PV.sbt_unidoc),
      SbtPlugin("com.typesafe.sbt", "sbt-site", PV.sbt_site),
      SbtPlugin("com.typesafe.sbt", "sbt-ghpages", PV.sbt_ghpages),
      SbtPlugin("io.github.jonas", "sbt-paradox-material-theme", PV.sbt_paradox_material_theme),
      SbtPlugin("org.scalameta", "sbt-mdoc", PV.sbt_mdoc),
    )
  )
}
