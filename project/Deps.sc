import $ivy.`io.7mind.izumi.sbt:sbtgen_2.13:0.0.97`
import izumi.sbtgen._
import izumi.sbtgen.model._

object Izumi {

  object V {
    val izumi_reflect = Version.VExpr("V.izumi_reflect")
    val sbtgen = Version.VExpr("V.sbtgen")
    val collection_compat = Version.VExpr("V.collection_compat")
    val kind_projector = Version.VExpr("V.kind_projector")
    val scalatest = Version.VExpr("V.scalatest")
    val cats = Version.VExpr("V.cats")
    val cats_effect = Version.VExpr("V.cats_effect")
    val discipline = Version.VExpr("V.discipline")
    val discipline_scalatest = Version.VExpr("V.discipline_scalatest")
    val zio = Version.VExpr("V.zio")
    val zio_interop_cats = Version.VExpr("V.zio_interop_cats")
    val monix = Version.VExpr("V.monix")
    val monix_bio = Version.VExpr("V.monix_bio")
    val circe = Version.VExpr("V.circe")
    val circe_generic_extras = Version.VExpr("V.circe_generic_extras")
    val circe_derivation = Version.VExpr("V.circe_derivation")
    val pureconfig = Version.VExpr("V.pureconfig")
    val magnolia = Version.VExpr("V.magnolia")
    val jawn = Version.VExpr("V.jawn")
    val doobie = Version.VExpr("V.doobie")
    val classgraph = Version.VExpr("V.classgraph")
    val slf4j = Version.VExpr("V.slf4j")
    val typesafe_config = Version.VExpr("V.typesafe_config")
    val bytebuddy = Version.VExpr("V.bytebuddy")
    val scala_java_time = Version.VExpr("V.scala_java_time")
    val scalamock = Version.VExpr("V.scalamock")
    val docker_java = Version.VExpr("V.docker_java")
  }

  object PV {
    val sbt_mdoc = Version.VExpr("PV.sbt_mdoc")
    val sbt_paradox = Version.VExpr("PV.sbt_paradox")
    val sbt_paradox_material_theme = Version.VExpr("PV.sbt_paradox_material_theme")
    val sbt_ghpages = Version.VExpr("PV.sbt_ghpages")
    val sbt_site = Version.VExpr("PV.sbt_site")
    val sbt_unidoc = Version.VExpr("PV.sbt_unidoc")
    val sbt_scoverage = Version.VExpr("PV.sbt_scoverage")
    val sbt_pgp = Version.VExpr("PV.sbt_pgp")
    val sbt_assembly = Version.VExpr("PV.sbt_assembly")

    val scala_js_version = Version.VExpr("PV.scala_js_version")
  }

  def entrypoint(args: Seq[String]) = {
    Entrypoint.main(izumi, settings, Seq("-o", ".") ++ args)
  }

  val settings = GlobalSettings(
    groupId = "io.7mind.izumi",
    sbtVersion = None,
    scalaJsVersion = Version.VExpr("PV.scala_js_version"),
  )

  object Deps {
    final val izumi_reflect = Library("dev.zio", "izumi-reflect", V.izumi_reflect, LibraryType.Auto)

    final val collection_compat = Library("org.scala-lang.modules", "scala-collection-compat", V.collection_compat, LibraryType.Auto)
    final val scalatest = Library("org.scalatest", "scalatest", V.scalatest, LibraryType.Auto) in Scope.Test.all

    final val cats_core = Library("org.typelevel", "cats-core", V.cats, LibraryType.Auto)
    final val cats_effect = Library("org.typelevel", "cats-effect", V.cats_effect, LibraryType.Auto)
    final val cats_all = Seq(
      cats_core,
      cats_effect,
    )
    final val cats_effect_laws = Library("org.typelevel", "cats-effect-laws", V.cats_effect, LibraryType.Auto) in Scope.Test.all
    final val cats_effect_testkit = Library("org.typelevel", "cats-effect-testkit", V.cats_effect, LibraryType.Auto) in Scope.Test.all

    final val circe_core = Library("io.circe", "circe-core", V.circe, LibraryType.Auto)
    final val circe_parser = Library("io.circe", "circe-parser", V.circe, LibraryType.Auto)
    final val circe_literal = Library("io.circe", "circe-literal", V.circe, LibraryType.Auto)
    final val circe_generic = Library("io.circe", "circe-generic", V.circe, LibraryType.Auto)
    final val circe_derivation_scala2 = Library("io.circe", "circe-derivation", V.circe_derivation, LibraryType.Auto) in Scope.Compile.all.scalaVersion(
      ScalaVersionScope.AllScala2
    )

    final val discipline = Library("org.typelevel", "discipline-core", V.discipline, LibraryType.Auto) in Scope.Test.all
    final val discipline_scalatest = Library("org.typelevel", "discipline-scalatest", V.discipline_scalatest, LibraryType.Auto) in Scope.Test.all

    final val pureconfig_core = Library("com.github.pureconfig", "pureconfig-core", V.pureconfig, LibraryType.Auto) in Scope.Compile.all
    final val pureconfig_magnolia = Library("com.github.pureconfig", "pureconfig-magnolia", V.pureconfig, LibraryType.Auto) in Scope.Compile.all.scalaVersion(
      ScalaVersionScope.AllScala2
    )
    final val magnolia = Library("com.propensive", "magnolia", V.magnolia, LibraryType.Auto) in Scope.Compile.all.scalaVersion(
      ScalaVersionScope.AllScala2
    )

    final val zio_core = Library("dev.zio", "zio", V.zio, LibraryType.Auto)
      .more(LibSetting.Raw("""excludeAll("dev.zio" %% "izumi-reflect")"""))
    final val zio_interop_cats = Library("dev.zio", "zio-interop-cats", V.zio_interop_cats, LibraryType.Auto)
      .more(LibSetting.Raw("""excludeAll("dev.zio" %% "izumi-reflect")"""))
    final val zio_all = Seq(zio_core, zio_interop_cats)
//    final val monix = Library("io.monix", "monix", V.monix, LibraryType.Auto)
//    final val monix_bio = Library("io.monix", "monix-bio", V.monix_bio, LibraryType.Auto)
//    final val monix_all = Seq(monix, monix_bio)
    // FIXME Disable monix due to lack of CE3 support as of now, see:
    //   https://github.com/monix/monix/issues/1502
    //   https://github.com/monix/monix/pull/1533
    final val monix_all = Seq.empty[Library]

    final val typesafe_config = Library("com.typesafe", "config", V.typesafe_config, LibraryType.Invariant) in Scope.Compile.all
    final val jawn = Library("org.typelevel", "jawn-parser", V.jawn, LibraryType.AutoJvm)

    final val scala_sbt = Library("org.scala-sbt", "sbt", Version.VExpr("sbtVersion.value"), LibraryType.Invariant)
    final val scala_compiler = Library("org.scala-lang", "scala-compiler", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala3_compiler = Library("org.scala-lang", "scala3-compiler", Version.VExpr("scalaVersion.value"), LibraryType.AutoJvm) in Scope.Provided.all.scalaVersion(
      ScalaVersionScope.AllScala3
    )
    final val scala_library = Library("org.scala-lang", "scala-library", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_reflect = Library("org.scala-lang", "scala-reflect", Version.VExpr("scalaVersion.value"), LibraryType.Invariant) in Scope.Provided.all.scalaVersion(
      ScalaVersionScope.AllScala2
    )

    final val bytebuddy = Library("net.bytebuddy", "byte-buddy", V.bytebuddy, LibraryType.Invariant) in Scope.Compile.jvm

    final val projector = Library("org.typelevel", "kind-projector", V.kind_projector, LibraryType.Invariant)
      .more(LibSetting.Raw("cross CrossVersion.full"))

    final val fast_classpath_scanner = Library("io.github.classgraph", "classgraph", V.classgraph, LibraryType.Invariant) in Scope.Compile.jvm
    final val scala_java_time = Library("io.github.cquiroz", "scala-java-time", V.scala_java_time, LibraryType.Auto)
    final val scalamock = Library("org.scalamock", "scalamock", V.scalamock, LibraryType.Auto)

    final val slf4j_api = Library("org.slf4j", "slf4j-api", V.slf4j, LibraryType.Invariant) in Scope.Compile.jvm
    final val slf4j_simple = Library("org.slf4j", "slf4j-simple", V.slf4j, LibraryType.Invariant) in Scope.Test.jvm

    val doobie_all = Seq(
      Library("org.tpolecat", "doobie-core", V.doobie, LibraryType.Auto),
      Library("org.tpolecat", "doobie-postgres", V.doobie, LibraryType.Auto),
    )

    val docker_java_core = Library("com.github.docker-java", "docker-java-core", V.docker_java, LibraryType.Invariant)
    val docker_java_transport_zerodep = Library("com.github.docker-java", "docker-java-transport-zerodep", V.docker_java, LibraryType.Invariant)

    val javaXInject = Library("javax.inject", "javax.inject", "1", LibraryType.Invariant)
  }

  import Deps._

  // DON'T REMOVE, these variables are read from CI build (build.sh)
  final val scala212 = ScalaVersion("2.12.17")
  final val scala213 = ScalaVersion("2.13.10")
  final val scala300 = ScalaVersion("3.2.1")

  object Groups {
    final val fundamentals = Set(Group("fundamentals"))
    final val distage = Set(Group("distage"))
    final val logstage = Set(Group("logstage"))
    final val docs = Set(Group("docs"))
    final val sbt = Set(Group("sbt"))
  }

  object Targets {
    // switch order to use 2.12 in IDEA
//    val targetScala = Seq(scala212, scala213)
    val targetScala = Seq(scala213, scala212)
    val targetScala3 = Seq(scala300, scala213, scala212)

    private val jvmPlatform = PlatformEnv(
      platform = Platform.Jvm,
      language = targetScala,
      settings = Seq.empty,
    )
    private val jsPlatform = PlatformEnv(
      platform = Platform.Js,
      language = targetScala,
      settings = Seq(
        "coverageEnabled" := false,
        "scalaJSLinkerConfig" in (SettingScope.Project, Platform.Js) := "{ scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }".raw,
      ),
    )

    private val jvmPlatform3 = PlatformEnv(
      platform = Platform.Jvm,
      language = targetScala3,
      settings = Seq.empty,
    )
    private val jsPlatform3 = PlatformEnv(
      platform = Platform.Js,
      language = targetScala3,
      settings = Seq(
        "coverageEnabled" := false,
        "scalaJSLinkerConfig" in (SettingScope.Project, Platform.Js) := "{ scalaJSLinkerConfig.value.withModuleKind(ModuleKind.CommonJSModule) }".raw,
      ),
    )

    private val jvmPlatformSbt = PlatformEnv(
      platform = Platform.Jvm,
      language = Seq(scala212),
      settings = Seq(
        "coverageEnabled" := false
      ),
    )
    final val cross = Seq(jvmPlatform, jsPlatform)
    final val jvm = Seq(jvmPlatform)
    final val js = Seq(jsPlatform)

    final val cross3 = Seq(jvmPlatform3, jsPlatform3)
    final val jvm3 = Seq(jvmPlatform3)
    final val js3 = Seq(jsPlatform3)

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

      final val outOfSource = Seq(
//        "target" := s"""baseDirectory.in(LocalProject("${Projects.root.id.value}")).value.toPath().resolve("target").resolve(baseDirectory.in(LocalProject("${Projects
//          .root.id.value}")).value.toPath().relativize(baseDirectory.value.toPath)).toFile""".raw
      )

      final val crossScalaSources = Defaults.CrossScalaPlusSources

      final val topLevelSettings = Seq()

      final val sharedAggSettings = outOfSource ++ Seq(
        "crossScalaVersions" := Targets.targetScala.map(_.value),
        "scalaVersion" := "crossScalaVersions.value.head".raw,
      )

      final val rootSettings = Defaults.RootOptions ++ Defaults.SbtMetaRootOptions ++ Seq(
//        "target" := s"""baseDirectory.in(LocalProject("${Projects.root.id.value}")).value.toPath().resolve("target").resolve("${Projects
//          .root.id.value}").toFile""".raw,
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
        "credentials" in SettingScope.Build ++=
          """
            |{
            |val credTarget = Path.userHome / ".sbt" / "secrets" / "credentials.sonatype-nexus.properties"
            |if (credTarget.exists) {
            |  Seq(Credentials(credTarget))
            |} else {
            |  Seq.empty
            |}
            |}""".stripMargin.raw,
        "homepage" in SettingScope.Build := """Some(url("https://izumi.7mind.io"))""".raw,
        "licenses" in SettingScope.Build := """Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))""".raw,
        "developers" in SettingScope.Build :=
          """List(
          Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/7mind"), email = "team@7mind.io"),
        )""".raw,
        "scmInfo" in SettingScope.Build := """Some(ScmInfo(url("https://github.com/7mind/izumi"), "scm:git:https://github.com/7mind/izumi.git"))""".raw,

        // scala-steward workaround
        // add sbtgen version to sbt build to allow scala-steward to find it and update it in .sc files
        // https://github.com/scala-steward-org/scala-steward/issues/696#issuecomment-545800968
        "libraryDependencies" += s""""io.7mind.izumi.sbt" % "sbtgen_2.13" % "${Version.SbtGen.value}" % Provided""".raw,
      )

      final val sharedSettings = Defaults.SbtMetaSharedOptions ++ outOfSource ++ crossScalaSources ++ Seq(
        "testOptions" in SettingScope.Test += """Tests.Argument("-oDF")""".raw,
        "scalacOptions" ++= Seq(
          SettingKey(Some(scala212), None) := Seq[Const]("-Wconf:any:error") ++ Defaults.Scala212Options,
          SettingKey(Some(scala213), None) := Seq[Const]("-Wconf:any:error") ++ Defaults.Scala213Options ++ Seq[Const](
            "-Wunused:-synthetics"
          ),
          SettingKey(Some(scala300), None) := Seq[Const]("-Yretain-trees", "-language:3.2") ++ Defaults.Scala3Options,
          SettingKey.Default := Const.EmptySeq,
        ),
        "scalacOptions" -= "-Wconf:any:warning",
        "scalacOptions" += "-Wconf:cat=deprecation:warning",
        "scalacOptions" += "-Wconf:msg=nowarn:silent",
        "scalacOptions" += "-Wconf:msg=parameter.value.x\\\\$4.in.anonymous.function.is.never.used:silent",
        "scalacOptions" += "-Wconf:msg=package.object.inheritance:silent",
        "scalacOptions" += "-Wconf:msg=is.eta.expanded.even.though:silent",
        "scalacOptions" in SettingScope.Raw("Compile / sbt.Keys.doc") -= "-Wconf:any:error",
        "scalacOptions" ++= Seq(
          """s"-Xmacro-settings:scalatest-version=${V.scalatest}"""".raw,
          """s"-Xmacro-settings:is-ci=${insideCI.value}"""".raw,
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
          SettingKey.Default := Const.EmptySeq,
        ),
        "publishArtifact" in SettingScope.Raw("Compile / packageDoc") := Seq(
          SettingKey(Some(scala300), None) := false,
          SettingKey.Default := true,
        ),
        "publishArtifact" in SettingScope.Raw("Test / packageDoc") := false,
      )

    }

    object fundamentals {
      final val id = ArtifactId("fundamentals")
      final val basePath = Seq("fundamentals")

      final val collections = ArtifactId("fundamentals-collections")
      final val platform = ArtifactId("fundamentals-platform")
      final val language = ArtifactId("fundamentals-language")
      final val functional = ArtifactId("fundamentals-functional")
      final val bio = ArtifactId("fundamentals-bio")
      final val orphans = ArtifactId("fundamentals-orphans")
      final val literals = ArtifactId("fundamentals-literals")

      final val typesafeConfig = ArtifactId("fundamentals-typesafe-config")
      final val reflection = ArtifactId("fundamentals-reflection")
      final val jsonCirce = ArtifactId("fundamentals-json-circe")

      final lazy val basics = Seq(
        platform,
        collections,
        functional,
      ).map(_ in Scope.Runtime.all)
    }

    object distage {
      final val id = ArtifactId("distage")
      final val basePath = Seq("distage")

      final lazy val coreApi = ArtifactId("distage-core-api")
      final lazy val proxyBytebuddy = ArtifactId("distage-core-proxy-bytebuddy")
      final lazy val core = ArtifactId("distage-core")
      final lazy val config = ArtifactId("distage-extension-config")
      final lazy val plugins = ArtifactId("distage-extension-plugins")
      final lazy val docker = ArtifactId("distage-framework-docker")
      final lazy val frameworkApi = ArtifactId("distage-framework-api")
      final lazy val framework = ArtifactId("distage-framework")
      final lazy val testkitCore = ArtifactId("distage-testkit-core")
      final lazy val testkitScalatest = ArtifactId("distage-testkit-scalatest")
      final lazy val extensionLogstage = ArtifactId("distage-extension-logstage")
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
        "sbtPlugin" := true
      )

      final lazy val izumi_deps = ArtifactId("sbt-izumi-deps")
    }

  }

  final val forkTests = Seq(
    "fork" in (SettingScope.Test, Platform.Jvm) := true
  )

  final lazy val fundamentals = Aggregate(
    name = Projects.fundamentals.id,
    artifacts = Seq(
      Artifact(
        name = Projects.fundamentals.functional,
        libs = Seq.empty,
        depends = Seq.empty,
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.collections,
        libs = Seq.empty,
        depends = Seq(
          Projects.fundamentals.functional
        ),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.literals,
        libs = Seq(
          scala_reflect
        ),
        depends = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.orphans,
        libs = allMonadsOptional ++ Seq(zio_interop_cats in Scope.Optional.all),
        depends = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.language,
        libs = Seq(
          scala_reflect,
          scala3_compiler,
        ),
        depends = Seq(
          Projects.fundamentals.literals
        ),
        settings = Seq.empty,
        plugins = Plugins(Seq(Plugin("ScalaJSBundlerPlugin", Platform.Js))),
      ),
      Artifact(
        name = Projects.fundamentals.platform,
        libs = Seq(
          scala_reflect
        ),
        depends = Seq(
          Projects.fundamentals.language in Scope.Compile.all,
          Projects.fundamentals.collections in Scope.Compile.all,
        ),
        settings = Seq(
          "npmDependencies" in (SettingScope.Test, Platform.Js) ++= Seq("hash.js" -> "1.1.7")
        ),
        plugins = Plugins(Seq(Plugin("ScalaJSBundlerPlugin", Platform.Js))),
      ),
      Artifact(
        name = Projects.fundamentals.jsonCirce,
        libs = Seq(
          circe_core in Scope.Compile.all,
          circe_generic in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala3),
          circe_derivation_scala2,
          scala_reflect,
        ) ++ Seq(
          jawn in Scope.Test.all,
          circe_literal in Scope.Test.all,
        ),
        depends = Seq(Projects.fundamentals.platform),
      ),
      Artifact(
        name = Projects.fundamentals.reflection,
        libs = Seq(izumi_reflect in Scope.Compile.all, scala_reflect),
        depends = Seq(
          Projects.fundamentals.platform,
          Projects.fundamentals.functional,
        ),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.bio,
        libs = allMonadsOptional ++
          Seq(cats_effect_laws, cats_effect_testkit, scalatest, discipline, discipline_scalatest) ++
          Seq(zio_interop_cats in Scope.Test.all) ++
          Seq(scala_java_time in Scope.Test.js),
        depends = Seq(
          Projects.fundamentals.language,
          Projects.fundamentals.orphans,
        ),
      ),
    ),
    pathPrefix = Projects.fundamentals.basePath,
    groups = Groups.fundamentals,
    defaultPlatforms = Targets.cross3,
  )

  final val allCatsOptional = cats_all.map(_ in Scope.Optional.all)
  final val allZioOptional = Seq(zio_core, izumi_reflect).map(_ in Scope.Optional.all)
  final val allMonads = cats_all ++ zio_all ++ Seq(izumi_reflect) ++ monix_all
  final val allMonadsOptional = allCatsOptional ++ allZioOptional ++ monix_all.map(_ in Scope.Optional.all)
  final val allMonadsTest = (cats_all ++ monix_all ++ Seq(zio_core, izumi_reflect)).map(_ in Scope.Test.all)

  final lazy val distage = Aggregate(
    name = Projects.distage.id,
    artifacts = Seq(
      Artifact(
        name = Projects.distage.coreApi,
        libs = allCatsOptional ++ allZioOptional ++ allMonadsTest ++ Seq(scala_reflect),
        depends = Seq(Projects.fundamentals.reflection, Projects.fundamentals.bio).map(_ in Scope.Compile.all),
        platforms = Targets.cross3,
      ),
      Artifact(
        name = Projects.distage.proxyBytebuddy,
        libs = Seq(bytebuddy),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all),
        platforms = Targets.jvm3,
      ),
      Artifact(
        name = Projects.distage.frameworkApi,
        libs = Seq(scala_reflect),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all),
        platforms = Targets.jvm3,
      ),
      Artifact( // broken on 3
        name = Projects.distage.core,
        libs = allMonadsOptional ++ Seq(
          zio_interop_cats in Scope.Optional.all
        ) ++ Seq(
          scala_java_time in Scope.Test.js,
          javaXInject in Scope.Test.all,
        ),
        depends = Seq(Projects.distage.coreApi in Scope.Compile.all, Projects.distage.proxyBytebuddy in Scope.Compile.jvm),
        platforms = Targets.cross3,
      ),
      Artifact(
        name = Projects.distage.config,
        libs = Seq(pureconfig_core, pureconfig_magnolia, magnolia) ++ Seq(scala_reflect),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all),
        platforms = Targets.jvm3,
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.distage.extensionLogstage,
        libs = Seq(zio_core in Scope.Test.all),
        depends = Seq(Projects.distage.config, Projects.distage.coreApi).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all) ++
          Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.cross3,
      ),
      Artifact(
        name = Projects.distage.plugins,
        libs = Seq(fast_classpath_scanner) ++ Seq(scala_reflect),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all) ++
          Seq(Projects.distage.config, Projects.logstage.core).map(_ in Scope.Test.all),
        platforms = Targets.jvm3,
      ),
      Artifact(
        name = Projects.distage.framework,
        libs = allCatsOptional ++ allMonadsTest ++ Seq(scala_reflect),
        depends = Seq(Projects.distage.extensionLogstage, Projects.logstage.renderingCirce).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.frameworkApi, Projects.distage.plugins, Projects.distage.config).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.plugins).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm3,
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.distage.docker,
        libs = allMonadsTest ++ Seq(docker_java_core, docker_java_transport_zerodep).map(_ in Scope.Compile.jvm),
        depends = Seq(Projects.distage.core, Projects.distage.config, Projects.distage.frameworkApi, Projects.distage.extensionLogstage).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.testkitScalatest in Scope.Test.all),
        platforms = Targets.jvm3,
      ),
      Artifact(
        name = Projects.distage.testkitCore,
        libs = Nil,
        depends = Seq(Projects.distage.framework).map(_ in Scope.Compile.all),
        platforms = Targets.jvm3,
      ),
      Artifact(
        name = Projects.distage.testkitScalatest,
        libs = allMonadsOptional ++ Seq(
          scalamock in Scope.Test.all.scalaVersion(ScalaVersionScope.AllScala2),
          scalatest.dependency in Scope.Compile.all,
        ),
        depends = Seq(Projects.distage.testkitCore).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.plugins).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.framework).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm3,
        settings = Seq(
          // Ignore scala-xml version conflict between scoverage where scalatest requires scala-xml v2
          // and scoverage requires scala-xml v1 on Scala 2.12,
          // introduced when updating scoverage to 2.0.0 https://github.com/7mind/izumi/pull/1754
          "libraryDependencySchemes" += """"org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always""".raw
        ),
      ),
    ),
    pathPrefix = Projects.distage.basePath,
    defaultPlatforms = Targets.cross3,
    groups = Groups.distage,
  )

  final lazy val logstage = Aggregate(
    name = Projects.logstage.id,
    artifacts = Seq(
      Artifact(
        name = Projects.logstage.core,
        libs = Seq(scala_reflect) ++
          allCatsOptional ++ allZioOptional ++
          Seq(scala_java_time in Scope.Compile.js),
        depends = Seq(Projects.fundamentals.bio, Projects.fundamentals.platform).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.logstage.renderingCirce,
        libs = Seq(
          jawn in Scope.Test.all,
          circe_parser in Scope.Test.all,
          circe_literal in Scope.Test.all,
          circe_generic in Scope.Test.all,
          zio_core in Scope.Test.all,
        ),
        depends = Seq(Projects.fundamentals.jsonCirce).map(_ in Scope.Compile.all) ++ Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        name = Projects.logstage.adapterSlf4j,
        libs = Seq(slf4j_api),
        depends = Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm3,
        settings = Seq(
          "compileOrder" in SettingScope.Compile := "CompileOrder.Mixed".raw,
          "compileOrder" in SettingScope.Test := "CompileOrder.Mixed".raw,
          "classLoaderLayeringStrategy" in SettingScope.Test := "ClassLoaderLayeringStrategy.Flat".raw,
        ),
      ),
      Artifact(
        name = Projects.logstage.sinkSlf4j,
        libs = Seq(slf4j_api, slf4j_simple),
        depends = Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm3,
      ),
    ),
    pathPrefix = Projects.logstage.basePath,
    groups = Groups.logstage,
    defaultPlatforms = Targets.cross3,
  )

  val all = Seq(fundamentals, distage, logstage)

  final lazy val docs = Aggregate(
    name = Projects.docs.id,
    artifacts = Seq(
      Artifact(
        name = Projects.docs.microsite,
        libs = (allMonads ++ doobie_all).map(_ in Scope.Compile.all),
        depends = all.flatMap(_.artifacts).map(_.name in Scope.Compile.all).distinct,
        settings = Seq(
          // ignore microsite in IDEA
          """SettingKey[Boolean]("ide-skip-project")""" := true,
          "scalacOptions" -= "-Wconf:any:error",
          //  Disable `-Xsource:3` in docs due to mdoc failures:
          //
          //  ```
          //  error: basics.md:97 (mdoc generated code) could not find implicit value for parameter t: pprint.TPrint[zio.ZIO[zio.Has[zio.console.Console.Service],Throwable,β$0$]]
          //  val injector: Injector[RIO[Console, _]] = Injector[RIO[Console, _]](); $doc.binder(injector, 2, 4, 2, 12)
          //                                                                                    ^
          //
          //  error: basics.md:109 (mdoc generated code) could not find implicit value for parameter t: pprint.TPrint[zio.ZIO[zio.Has[zio.console.Console.Service],Throwable,β$0$]]
          //  val resource = injector.produce(plan); $doc.binder(resource, 4, 4, 4, 12)
          //                                                    ^
          //
          //  error: basics.md:1359 (mdoc generated code) could not find implicit value for parameter t: pprint.TPrint[zio.ZIO[zio.Has[zio.console.Console.Service],Throwable,β$9$]]
          //  val res51 = chooseInterpreters(true); $doc.binder(res51, 26, 0, 26, 24)
          //  ```
          "scalacOptions" -= "-Xsource:3",
          // enable for unidoc
          "scalacOptions" in SettingScope.Raw("Compile / sbt.Keys.doc") += "-Xsource:3",
          //
          "coverageEnabled" := false,
          "skip" in SettingScope.Raw("publish") := true,
          "DocKeys.prefix" :=
            """{if (isSnapshot.value) {
            (s => s"latest/snapshot/$s")
          } else {
            identity
          }}""".raw,
          "previewFixedPort" := "Some(9999)".raw,
          "gitRemoteRepo" := "git@github.com:7mind/izumi-microsite.git",
          "mdocIn" := """baseDirectory.value / "src/main/tut"""".raw,
          "sourceDirectory" in SettingScope.Raw("(Compile / paradox)") := "mdocOut.value".raw,
          "mdocExtraArguments" ++= Seq(" --no-link-hygiene"),
          "mappings" in SettingScope.Raw("SitePlugin.autoImport.makeSite") :=
            """{
            (SitePlugin.autoImport.makeSite / mappings)
              .dependsOn(mdoc.toTask(" "))
              .value
          }""".raw,
          "version" in SettingScope.Raw("(Compile / paradox)") := "version.value".raw,
          SettingDef.RawSettingDef("ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Compile)"),
          SettingDef.RawSettingDef("addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName)"),
          SettingDef.RawSettingDef(
            "ScalaUnidoc / unidoc / unidocProjectFilter := inAggregates(`fundamentals-jvm`, transitive = true) || inAggregates(`distage-jvm`, transitive = true) || inAggregates(`logstage-jvm`, transitive = true)"
          ),
          SettingDef.RawSettingDef("""Compile / ParadoxMaterialThemePlugin.autoImport.paradoxMaterialTheme ~= {
            _.withCopyright("7mind.io")
              .withRepository(uri("https://github.com/7mind/izumi"))
            //        .withColor("222", "434343")
          }"""),
          "siteSubdirName" in SettingScope.Raw("ScalaUnidoc") := """DocKeys.prefix.value("api")""".raw,
          "siteSubdirName" in SettingScope.Raw("Paradox") := """DocKeys.prefix.value("")""".raw,
          SettingDef.RawSettingDef("""paradoxProperties ++= Map(
            "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value("api")}",
            "scaladoc.base_url" -> s"/${DocKeys.prefix.value("api")}",
            "izumi.version" -> version.value,
          )"""),
          SettingDef.RawSettingDef(
            """ghpagesCleanSite / excludeFilter :=
            new FileFilter {
              def accept(f: File): Boolean = {
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("latest")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("distage")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("logstage")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("idealingua")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("bio")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("sbt")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("manifesto")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("pper")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("api")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("assets")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("lib")) ||
                  f.toPath.startsWith(ghpagesRepository.value.toPath.resolve("search")) ||
                  f.toPath.startsWith((ghpagesRepository.value / "media").toPath) ||
                  (ghpagesRepository.value / "paradox.json").getCanonicalPath == f.getCanonicalPath ||
                  (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath ||
                  (ghpagesRepository.value / ".nojekyll").getCanonicalPath == f.getCanonicalPath ||
                  (ghpagesRepository.value / "index.html").getCanonicalPath == f.getCanonicalPath ||
                  (ghpagesRepository.value / "README.md").getCanonicalPath == f.getCanonicalPath
              }
            }"""
          ),
        ),
        plugins = Plugins(
          enabled = Seq(
            Plugin("ScalaUnidocPlugin"),
            Plugin("ParadoxSitePlugin"),
            Plugin("SitePlugin"),
            Plugin("GhpagesPlugin"),
            Plugin("ParadoxMaterialThemePlugin"),
            Plugin("PreprocessPlugin"),
            Plugin("MdocPlugin"),
          ),
          disabled = Seq(Plugin("ScoverageSbtPlugin")),
        ),
      )
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
        ),
      )
    ),
    pathPrefix = Projects.sbtplugins.basePath,
    groups = Groups.sbt,
    defaultPlatforms = Targets.jvmSbt,
    enableProjectSharedAggSettings = false,
    settings = Seq(
      "crossScalaVersions" := "Nil".raw,
      "scalaVersion" := scala212.value,
    ),
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
    topLevelSettings = Projects.root.topLevelSettings,
    sharedSettings = Projects.root.sharedSettings,
    sharedAggSettings = Projects.root.sharedAggSettings,
    rootSettings = Projects.root.rootSettings,
    imports = Seq(
      Import("com.github.sbt.git.SbtGit.GitKeys._")
    ),
    globalLibs = Seq(
      ScopedLibrary(projector, FullDependencyScope(Scope.Compile, Platform.All, ScalaVersionScope.AllScala2), compilerPlugin = true),
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
      SbtPlugin("com.github.sbt", "sbt-ghpages", PV.sbt_ghpages),
      SbtPlugin("com.lightbend.paradox", "sbt-paradox", PV.sbt_paradox),
      SbtPlugin("io.github.jonas", "sbt-paradox-material-theme", PV.sbt_paradox_material_theme),
      SbtPlugin("org.scalameta", "sbt-mdoc", PV.sbt_mdoc),
    ),
  )
}
