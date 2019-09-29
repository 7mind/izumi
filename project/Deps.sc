import $ivy.`io.7mind.izumi.sbt::sbtgen:0.0.34`, izumi.sbtgen._, izumi.sbtgen.model._

object Izumi {

  object V {
    val scala_212 = Version.VExpr("V.scala_212")
    val scala_213 = Version.VExpr("V.scala_213")
    val collection_compat = Version.VExpr("V.collection_compat")
    val kind_projector = Version.VExpr("V.kind_projector")
    val scalatest = Version.VExpr("V.scalatest")
    val boopickle = Version.VExpr("V.boopickle")
    val shapeless = Version.VExpr("V.shapeless")
    val cats = Version.VExpr("V.cats")
    val cats_effect = Version.VExpr("V.cats_effect")
    val zio = Version.VExpr("V.zio")
    val zio_interop_cats = Version.VExpr("V.zio_interop_cats")
    val circe = Version.VExpr("V.circe")
    val circe_generic_extras = Version.VExpr("V.circe_generic_extras")
    val circe_derivation = Version.VExpr("V.circe_derivation")
    val jawn = Version.VExpr("V.jawn")
    val http4s = Version.VExpr("V.http4s")
    val scalameta = Version.VExpr("V.scalameta")
    val fastparse = Version.VExpr("V.fastparse")
    val scala_xml = Version.VExpr("V.scala_xml")
    val asynchttpclient = Version.VExpr("V.asynchttpclient")
    val classgraph = Version.VExpr("V.classgraph")
    val slf4j = Version.VExpr("V.slf4j")
    val typesafe_config = Version.VExpr("V.typesafe_config")
    val cglib_nodep = Version.VExpr("V.cglib_nodep")
    val scala_java_time = Version.VExpr("V.scala_java_time")
  }


  def entrypoint(args: Seq[String]) = {
    Entrypoint.main(izumi, settings, Seq("-o", ".") ++ args)
  }

  val settings = GlobalSettings(
    groupId = "io.7mind.izumi",
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

    final val circe = Seq(
      Library("io.circe", "circe-core", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-parser", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-literal", V.circe, LibraryType.Auto),
      Library("io.circe", "circe-generic-extras", V.circe_generic_extras, LibraryType.Auto),
      Library("io.circe", "circe-derivation", V.circe_derivation, LibraryType.Auto),
    ).map(_ in Scope.Compile.all)

    final val zio_core = Library("dev.zio", "zio", V.zio, LibraryType.Auto)
    final val zio_interop_cats = Library("dev.zio", "zio-interop-cats", V.zio_interop_cats, LibraryType.Auto)
    final val zio_all = Seq(
      zio_core,
      zio_interop_cats,
    )

    final val typesafe_config = Library("com.typesafe", "config", V.typesafe_config, LibraryType.Invariant) in Scope.Compile.all
    final val boopickle = Library("io.suzaku", "boopickle", V.boopickle, LibraryType.Auto) in Scope.Compile.all
    final val jawn = Library("org.typelevel", "jawn-parser", V.jawn, LibraryType.AutoJvm)

    final val scala_sbt = Library("org.scala-sbt", "sbt", Version.VExpr("sbtVersion.value"), LibraryType.Invariant)
    final val scala_compiler = Library("org.scala-lang", "scala-compiler", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_library = Library("org.scala-lang", "scala-library", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_reflect = Library("org.scala-lang", "scala-reflect", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_xml = Library("org.scala-lang.modules", "scala-xml", V.scala_xml, LibraryType.Auto) in Scope.Compile.all
    final val scalameta = Library("org.scalameta", "scalameta", V.scalameta, LibraryType.Auto) in Scope.Compile.all

    final val cglib_nodep = Library("cglib", "cglib-nodep", V.cglib_nodep, LibraryType.Invariant) in Scope.Compile.jvm


    final val projector = Library("org.typelevel", "kind-projector", "0.10.3", LibraryType.AutoJvm)

    final val fast_classpath_scanner = Library("io.github.classgraph", "classgraph", V.classgraph, LibraryType.Invariant) in Scope.Compile.jvm
    final val scala_java_time = Library("io.github.cquiroz", "scala-java-time", V.scala_java_time, LibraryType.Auto) in Scope.Compile.all
    final val shapeless = Library("com.chuusai", "shapeless", V.shapeless, LibraryType.Auto) in Scope.Compile.all

    final val slf4j_api = Library("org.slf4j", "slf4j-api", V.slf4j, LibraryType.Invariant) in Scope.Compile.jvm
    final val slf4j_simple = Library("org.slf4j", "slf4j-simple", V.slf4j, LibraryType.Invariant) in Scope.Test.jvm

    final val fastparse = Library("com.lihaoyi", "fastparse", V.fastparse, LibraryType.Auto) in Scope.Compile.all

    final val http4s_client = Seq(
      Library("org.http4s", "http4s-blaze-client", V.http4s, LibraryType.Auto),
    )

    val http4s_server = Seq(
      Library("org.http4s", "http4s-dsl", V.http4s, LibraryType.Auto),
      Library("org.http4s", "http4s-circe", V.http4s, LibraryType.Auto),
      Library("org.http4s", "http4s-blaze-server", V.http4s, LibraryType.Auto),
    )

    val http4s_all = (http4s_server ++ http4s_client)

    val asynchttpclient = Library("org.asynchttpclient", "async-http-client", V.asynchttpclient, LibraryType.Invariant)
  }

  import Deps._

  final val scala212 = ScalaVersion("2.12.10")
  final val scala212doc = ScalaVersion("2.12.8")
  final val scala213 = ScalaVersion("2.13.0")

  object Groups {
    final val fundamentals = Set(Group("fundamentals"))
    final val distage = Set(Group("distage"))
    final val logstage = Set(Group("logstage"))
    final val idealingua = Set(Group("idealingua"))
    final val docs = Set(Group("docs"))
    final val sbt = Set(Group("sbt"))
  }

  object Targets {
    val targetScala = Seq(scala212, scala213)
    private val jvmPlatform = PlatformEnv(
      platform = Platform.Jvm,
      language = targetScala,
    )
    private val jvmPlatformDoc = PlatformEnv(
      platform = Platform.Jvm,
      language = Seq(scala212doc),
    )
    private val jvmPlatformSbt = PlatformEnv(
      platform = Platform.Jvm,
      language = Seq(scala212),
      settings = Seq(
        "coverageEnabled" := false,
      ),
    )
    private val jsPlatform = PlatformEnv(
      platform = Platform.Js,
      language = targetScala,
      settings = Seq(
        "coverageEnabled" := false,
        "scalaJSModuleKind" in(SettingScope.Project, Platform.Js) := "ModuleKind.CommonJSModule".raw,
      ),
    )
    final val cross = Seq(jvmPlatform, jsPlatform)
    final val jvm = Seq(jvmPlatform)
    final val jvmDoc = Seq(jvmPlatformDoc)
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
        Seq.empty,
        Seq(Plugin("AssemblyPlugin", Platform.All)),
      )
      final val settings = Seq()

      final val sharedAggSettings = Seq(
        "crossScalaVersions" := Targets.targetScala.map(_.value),
        "scalaVersion" := "crossScalaVersions.value.head".raw,
      )

      final val docSettings = Seq(
        "crossScalaVersions" := Seq(scala212doc.value),
        "scalaVersion" := "crossScalaVersions.value.head".raw,
      )

      final val sharedRootSettings = Defaults.SharedOptions ++ Seq(
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
        SettingDef.RawSettingDef("""scalacOptions in ThisBuild ++= Seq("-Ybackend-parallelism", math.max(1, sys.runtime.availableProcessors() - 1).toString)"""),
        "scalacOptions" in SettingScope.Build += s"""${"\""*3}-Xmacro-settings:scalatest-version=${V.scalatest}${"\""*3}""".raw,
      )

      final val sharedSettings = Defaults.SbtMeta ++ Seq(
        "testOptions" in SettingScope.Test += """Tests.Argument("-oDF")""".raw,
        "scalacOptions" ++= Seq(
          SettingKey(Some(scala212), None) := Defaults.Scala212Options,
          SettingKey(Some(scala212doc), None) := Defaults.Scala212Options,
          SettingKey(Some(scala213), None) := Defaults.Scala213Options,
          SettingKey.Default := Const.EmptySeq
        ),
      )
    }

    object fundamentals {
      final val id = ArtifactId("fundamentals")
      final val basePath = Seq("fundamentals")

      final val fundamentalsCollections = ArtifactId("fundamentals-collections")
      final val fundamentalsPlatform = ArtifactId("fundamentals-platform")
      final val functional = ArtifactId("fundamentals-functional")
      final val bio = ArtifactId("fundamentals-bio")

      final val typesafeConfig = ArtifactId("fundamentals-typesafe-config")
      final val reflection = ArtifactId("fundamentals-reflection")
      final val fundamentalsJsonCirce = ArtifactId("fundamentals-json-circe")

      final lazy val basics = Seq(
        fundamentalsPlatform,
        functional,
        fundamentalsCollections,
      ).map(_ in Scope.Runtime.all)
    }

    object distage {
      final val id = ArtifactId("distage")
      final val basePath = Seq("distage")

      final lazy val model = ArtifactId("distage-model")
      final lazy val proxyCglib = ArtifactId("distage-proxy-cglib")
      final lazy val core = ArtifactId("distage-core")
      final lazy val config = ArtifactId("distage-config")
      final lazy val rolesApi = ArtifactId("distage-roles-api")
      final lazy val plugins = ArtifactId("distage-plugins")
      final lazy val roles = ArtifactId("distage-roles")
      final lazy val static = ArtifactId("distage-static")
      final lazy val testkit = ArtifactId("distage-testkit")
    }

    object logstage {
      final val id = ArtifactId("logstage")
      final val basePath = Seq("logstage")

      final lazy val api = ArtifactId("logstage-api")
      final lazy val core = ArtifactId("logstage-core")
      final lazy val renderingCirce = ArtifactId("logstage-rendering-circe")
      final lazy val di = ArtifactId("logstage-di")
      final lazy val config = ArtifactId("logstage-config")
      final lazy val adapterSlf4j = ArtifactId("logstage-adapter-slf4j")
      final lazy val sinkSlf4j = ArtifactId("logstage-sink-slf4j")
    }

    object idealingua {
      final val id = ArtifactId("idealingua")
      final val basePath = Seq("idealingua-v1")

      final val model = ArtifactId("idealingua-v1-model")
      final val core = ArtifactId("idealingua-v1-core")
      final val runtimeRpcScala = ArtifactId("idealingua-v1-runtime-rpc-scala")
      final val testDefs = ArtifactId("idealingua-v1-test-defs")
      final val transpilers = ArtifactId("idealingua-v1-transpilers")
      final val runtimeRpcHttp4s = ArtifactId("idealingua-v1-runtime-rpc-http4s")
      final val runtimeRpcTypescript = ArtifactId("idealingua-v1-runtime-rpc-typescript")
      final val runtimeRpcCSharp = ArtifactId("idealingua-v1-runtime-rpc-csharp")
      final val runtimeRpcGo = ArtifactId("idealingua-v1-runtime-rpc-go")
      final val compiler = ArtifactId("idealingua-v1-compiler")
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

  final lazy val fundamentals = Aggregate(
    Projects.fundamentals.id,
    Seq(
      Artifact(
        Projects.fundamentals.fundamentalsCollections,
        Seq.empty,
        Seq.empty,
      ),
      Artifact(
        Projects.fundamentals.fundamentalsPlatform,
        Seq.empty,
        Seq(
          Projects.fundamentals.fundamentalsCollections in Scope.Compile.all
        ),
        settings = Seq(
          "npmDependencies" in(SettingScope.Compile, Platform.Js) ++= Seq("hash.js" -> "1.1.7"),
        ),
        plugins = Plugins(Seq(Plugin("ScalaJSBundlerPlugin", Platform.Js))),
      ),
      Artifact(
        Projects.fundamentals.functional,
        Seq.empty,
        Seq.empty,
      ),
      Artifact(
        Projects.fundamentals.bio,
        (cats_all ++ Seq(zio_core)).map(_ in Scope.Optional.all),
        Seq(Projects.fundamentals.functional in Scope.Runtime.all),
      ),
      Artifact(
        Projects.fundamentals.typesafeConfig,
        Seq(typesafe_config, scala_reflect in Scope.Compile.jvm),
        Projects.fundamentals.basics ++ Seq(Projects.fundamentals.reflection in Scope.Runtime.jvm),
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.fundamentals.reflection,
        Seq(boopickle, scala_reflect in Scope.Provided.all),
        Projects.fundamentals.basics,
      ),
      Artifact(
        Projects.fundamentals.fundamentalsJsonCirce,
        circe ++ Seq(jawn in Scope.Compile.js),
        Projects.fundamentals.basics,
      ),
    ),
    pathPrefix = Projects.fundamentals.basePath,
    groups = Groups.fundamentals,
    defaultPlatforms = Targets.cross,
  )


  final val allMonads = (cats_all ++ Seq(zio_core)).map(_ in Scope.Optional.all)
  final val allMonadsTest = (cats_all ++ Seq(zio_core)).map(_ in Scope.Test.all)


  final lazy val distage = Aggregate(
    Projects.distage.id,
    Seq(
      Artifact(
        name = Projects.distage.model,
        libs = cats_all.map(_ in Scope.Optional.all) ++ Seq(scala_reflect in Scope.Compile.all),
        depends = Projects.fundamentals.basics ++ Seq(Projects.fundamentals.bio, Projects.fundamentals.reflection).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.proxyCglib,
        libs = Seq(cglib_nodep),
        depends = Projects.fundamentals.basics ++ Seq(Projects.distage.model).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.core,
        libs = Seq(cglib_nodep),
        depends = Seq(Projects.distage.model, Projects.distage.proxyCglib).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.config,
        libs = Seq(typesafe_config),
        depends = Seq(Projects.distage.model, Projects.fundamentals.typesafeConfig).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all),
      ),
      Artifact(
        name = Projects.distage.rolesApi,
        libs = Seq.empty,
        depends = Seq(Projects.distage.model).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.plugins,
        libs = Seq(fast_classpath_scanner),
        depends = Seq(Projects.distage.model).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ tin Scope.Test.all) ++
          Seq(Projects.distage.config, Projects.logstage.core).map(_ in Scope.Test.all),
      ),
      Artifact(
        name = Projects.distage.roles,
        libs = allMonads,
        depends = Seq(Projects.distage.rolesApi, Projects.logstage.di, Projects.logstage.adapterSlf4j, Projects.logstage.renderingCirce).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.plugins, Projects.distage.config).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        name = Projects.distage.static,
        libs = Seq.empty,
        depends = Seq(Projects.distage.core).map(_ tin Scope.Compile.all) ++ Seq(Projects.distage.roles).map(_ tin Scope.Test.all),
      ),
      Artifact(
        name = Projects.distage.testkit,
        libs = Seq(scalatest.dependency in Scope.Compile.all) ++ allMonads,
        depends =
          Seq(Projects.distage.config, Projects.distage.roles, Projects.distage.static, Projects.logstage.di).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.plugins).map(_ tin Scope.Compile.all),
        settings = Seq(
          "classLoaderLayeringStrategy" in SettingScope.Test := "ClassLoaderLayeringStrategy.Flat".raw,
        )
      ),
    ),
    pathPrefix = Projects.distage.basePath,
    defaultPlatforms = Targets.jvm,
    groups = Groups.distage,
  )

  final lazy val logstage = Aggregate(
    Projects.logstage.id,
    Seq(
      Artifact(
        Projects.logstage.api,
        Seq(scala_reflect in Scope.Provided.all) ++ Seq(scala_java_time),
        Seq(Projects.fundamentals.reflection).map(_ in Scope.Compile.all),
      ),
      Artifact(
        Projects.logstage.core,
        Seq(scala_reflect in Scope.Provided.all) ++
          Seq(cats_core, zio_core).map(_ in Scope.Optional.all) ++
          allMonadsTest,
        Seq(Projects.fundamentals.bio).map(_ in Scope.Compile.all) ++ Seq(Projects.logstage.api).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        Projects.logstage.renderingCirce,
        Seq.empty,
        Seq(Projects.fundamentals.fundamentalsJsonCirce).map(_ in Scope.Compile.all) ++ Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        Projects.logstage.di,
        Seq.empty,
        Seq(Projects.logstage.config, Projects.distage.config, Projects.distage.model).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all) ++
          Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
        groups = Groups.distage,
      ),
      Artifact(
        Projects.logstage.config,
        Seq.empty,
        Seq(Projects.fundamentals.typesafeConfig, Projects.logstage.core).map(_ in Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.logstage.adapterSlf4j,
        Seq(slf4j_api),
        Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
        settings = Seq(
          "compileOrder" in SettingScope.Compile := "CompileOrder.Mixed".raw,
          "compileOrder" in SettingScope.Test := "CompileOrder.Mixed".raw,
          "classLoaderLayeringStrategy" in SettingScope.Test := "ClassLoaderLayeringStrategy.Flat".raw,
        )
      ),
      Artifact(
        Projects.logstage.sinkSlf4j,
        Seq(slf4j_api, slf4j_simple),
        Seq(Projects.logstage.api).map(_ in Scope.Compile.all) ++ Seq(Projects.logstage.core).map(_ tin Scope.Test.all),
        platforms = Targets.jvm,
      )
    ),
    pathPrefix = Projects.logstage.basePath,
    groups = Groups.logstage,
    defaultPlatforms = Targets.cross,
  )

  final lazy val idealingua = Aggregate(
    Projects.idealingua.id,
    Seq(
      Artifact(
        Projects.idealingua.model,
        Seq.empty,
        Projects.fundamentals.basics,
      ),
      Artifact(
        Projects.idealingua.core,
        Seq(fastparse),
        Projects.fundamentals.basics ++ Seq(Projects.idealingua.model, Projects.fundamentals.reflection).map(_ in Scope.Compile.all),
      ),
      Artifact(
        Projects.idealingua.runtimeRpcScala,
        Seq(scala_reflect in Scope.Provided.all) ++ (cats_all ++ zio_all).map(_ in Scope.Compile.all),
        Projects.fundamentals.basics ++ Seq(Projects.fundamentals.bio, Projects.fundamentals.fundamentalsJsonCirce).map(_ in Scope.Compile.all),
      ),
      Artifact(
        Projects.idealingua.runtimeRpcHttp4s,
        (http4s_all ++ Seq(asynchttpclient)).map(_ in Scope.Compile.all),
        Seq(Projects.idealingua.runtimeRpcScala, Projects.logstage.core, Projects.logstage.adapterSlf4j).map(_ in Scope.Compile.all) ++
          Seq(Projects.idealingua.testDefs).map(_ in Scope.Test.jvm),
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.idealingua.transpilers,
        Seq(scala_xml, scalameta),
        Projects.fundamentals.basics ++
          Seq(Projects.fundamentals.fundamentalsJsonCirce, Projects.idealingua.core, Projects.idealingua.runtimeRpcScala).map(_ in Scope.Compile.all) ++
          Seq(Projects.idealingua.testDefs, Projects.idealingua.runtimeRpcTypescript, Projects.idealingua.runtimeRpcGo, Projects.idealingua.runtimeRpcCSharp).map(_ in Scope.Test.jvm),
        settings = forkTests
      ),
      Artifact(
        Projects.idealingua.testDefs,
        Seq.empty,
        Seq(Projects.idealingua.runtimeRpcScala).map(_ in Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.idealingua.runtimeRpcTypescript,
        Seq.empty,
        Seq.empty,
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.idealingua.runtimeRpcGo,
        Seq.empty,
        Seq.empty,
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.idealingua.runtimeRpcCSharp,
        Seq.empty,
        Seq.empty,
        platforms = Targets.jvm,
      ),
      Artifact(
        Projects.idealingua.compiler,
        Seq(typesafe_config),
        Seq(
          Projects.idealingua.transpilers,
          Projects.idealingua.runtimeRpcScala,
          Projects.idealingua.runtimeRpcTypescript,
          Projects.idealingua.runtimeRpcGo,
          Projects.idealingua.runtimeRpcCSharp,
          Projects.idealingua.testDefs,
        ).map(_ in Scope.Compile.all),
        platforms = Targets.jvm,
        plugins = Plugins(Seq(assemblyPluginJvm)),
        settings = Seq(
          "mainClass" in SettingScope.Raw("assembly") := """Some("izumi.idealingua.compiler.CommandlineIDLCompiler")""".raw,
          "assemblyMergeStrategy" in SettingScope.Raw("assembly") :=
            """{
              |      // FIXME: workaround for https://github.com/zio/interop-cats/issues/16
              |      case path if path.contains("zio/BuildInfo$.class") =>
              |        MergeStrategy.last
              |      case p =>
              |        (assemblyMergeStrategy in assembly).value(p)
              |}""".stripMargin.raw,
          "artifact" in SettingScope.Raw("(Compile, assembly)") :=
            """{
              |      val art = (artifact in(Compile, assembly)).value
              |      art.withClassifier(Some("assembly"))
              |}""".stripMargin.raw,
          SettingDef.RawSettingDef("addArtifact(artifact in(Compile, assembly), assembly)")
        )
      ),
    ),
    pathPrefix = Projects.idealingua.basePath,
    groups = Groups.idealingua,
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
        settings = Projects.root.docSettings ++ Seq(
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
          SettingDef.RawSettingDef("unidocProjectFilter in(ScalaUnidoc, unidoc) := inAggregates(`izumi-jvm`, transitive=true)"),

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
            "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value}/api/com/github/pshirshov/",
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
            }""")
        ),
        plugins = Plugins(
          enabled = Seq(
            Plugin("ScalaUnidocPlugin"),
            Plugin("ParadoxSitePlugin"),
            Plugin("SitePlugin"),
            Plugin("GhpagesPlugin"),
            Plugin("ParadoxMaterialThemePlugin"),
            Plugin("PreprocessPlugin"),
            Plugin("MdocPlugin")),
          disabled = Seq(Plugin("ScoverageSbtPlugin")))
      ),
    ),
    pathPrefix = Projects.docs.basePath,
    groups = Groups.docs,
    defaultPlatforms = Targets.jvmDoc,
    settings = Projects.root.docSettings,
    enableSharedSettings = false,
    dontIncludeInSuperAgg = true,
  )

  final lazy val sbtplugins = Aggregate(
    Projects.sbtplugins.id,
    Seq(
      Artifact(
        name = Projects.sbtplugins.izumi_deps,
        libs = Seq.empty,
        depends = Seq.empty,
        settings = Projects.sbtplugins.settings ++ Seq(
          SettingDef.RawSettingDef("""withBuildInfo("izumi.sbt.deps", "Izumi")""")
        ),
        plugins = Plugins(
          enabled = Seq.empty,
          disabled = Seq(Plugin("ScoverageSbtPlugin")))
      ),
    ),
    pathPrefix = Projects.sbtplugins.basePath,
    groups = Groups.sbt,
    defaultPlatforms = Targets.jvmSbt,
    enableSharedSettings = false,
    dontIncludeInSuperAgg = false,
  )

  val izumi: Project = Project(
    name = Projects.root.id,
    aggregates = Seq(
      fundamentals,
      distage,
      logstage,
      idealingua,
      docs,
      sbtplugins,
    ),
    settings = Projects.root.settings,
    sharedSettings = Projects.root.sharedSettings,
    sharedAggSettings = Projects.root.sharedAggSettings,
    sharedRootSettings = Projects.root.sharedRootSettings,
    imports = Seq.empty,
    globalLibs = Seq(
      ScopedLibrary(projector, FullDependencyScope(Scope.Compile, Platform.All), compilerPlugin = true),
      collection_compat in Scope.Compile.all,
      scalatest,
    ),
    rootPlugins = Projects.root.plugins,
    globalPlugins = Projects.plugins,
    pluginConflictRules = Map(assemblyPluginJvm.name -> true),
    appendPlugins = Defaults.SbtGenPlugins ++ Seq(
      SbtPlugin("com.eed3si9n", "sbt-assembly", "0.14.9"),
      SbtPlugin("com.jsuereth", "sbt-pgp", "2.0.0-M2"),
      SbtPlugin("org.scoverage", "sbt-scoverage", "1.6.0"),
      SbtPlugin("com.eed3si9n", "sbt-unidoc", "0.4.2"),
      SbtPlugin("com.typesafe.sbt", "sbt-site", "1.3.3"),
      SbtPlugin("com.typesafe.sbt", "sbt-ghpages", "0.6.3"),
      SbtPlugin("io.github.jonas", "sbt-paradox-material-theme", "0.6.0"),
      SbtPlugin("org.scalameta", "sbt-mdoc", "1.3.2"),
    )
  )
}

