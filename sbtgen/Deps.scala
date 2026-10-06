import izumi.sbtgen.*
import izumi.sbtgen.model.*

object Izumi {

  def main(args: Array[String]): Unit = {
    val nativeLinkSettings = if (args.contains("--native")) Projects.root.nativeLinkSettings else Seq.empty
    Entrypoint.main(izumi.copy(rootSettings = izumi.rootSettings ++ nativeLinkSettings), settings, Seq("-o", ".") ++ args.toSeq)
  }

  object V {
    val izumi_reflect = Version.VExpr("V.izumi_reflect")
    val sbtgen = Version.VExpr("V.sbtgen")
    val kind_projector = Version.VExpr("V.kind_projector")
    val scalatest = Version.VExpr("V.scalatest")
    val scalatestplus_scalacheck = Version.VExpr("V.scalatestplus_scalacheck")
    val cats = Version.VExpr("V.cats")
    val cats_effect = Version.VExpr("V.cats_effect")
    val scalac_compat_annotation = Version.VExpr("V.scalac_compat_annotation")
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
    val sbt_test_interface = Version.VExpr("V.sbt_test_interface")
    val scalajs_test_interface = Version.VExpr("V.scalajs_test_interface")
    val slf4j = Version.VExpr("V.slf4j")
    val typesafe_config = Version.VExpr("V.typesafe_config")
    val bytebuddy = Version.VExpr("V.bytebuddy")
    val scala_java_time = Version.VExpr("V.scala_java_time")
    val scalamock = Version.VExpr("V.scalamock")
    val docker_java = Version.VExpr("V.docker_java")
    val commons_compress = Version.VExpr("V.commons_compress")
    val scalajs_java_securerandom = Version.VExpr("V.scalajs_java_securerandom")
    val scalajs_macrotask_executor = Version.VExpr("V.scalajs_macrotask_executor")
    val portable_scala_reflect = Version.VExpr("V.portable_scala_reflect")
  }

  object PV {
    val sbt_mdoc = Version.VExpr("PV.sbt_mdoc")
    val sbt_paradox = Version.VExpr("PV.sbt_paradox")
    val sbt_ghpages = Version.VExpr("PV.sbt_ghpages")
    val sbt_site = Version.VExpr("PV.sbt_site")
    val sbt_unidoc = Version.VExpr("PV.sbt_unidoc")
    val sbt_scoverage = Version.VExpr("PV.sbt_scoverage")
    val sbt_pgp = Version.VExpr("PV.sbt_pgp")

    val scala_js_version = Version.VExpr("PV.scala_js_version")
  }

  val settings = GlobalSettings(
    groupId = "io.7mind.izumi",
    sbtTarget = SbtTarget.Sbt2,
    sbtVersion = Some("2.0.9"),
    scalaJsVersion = Version.VExpr("PV.scala_js_version"),
    scalaNativeVersion = Version.VConst("0.5.12"),
    crossProjectVersion = Version.VConst("1.4.0"),
    bundlerVersion = None,
    sbtJsDependenciesVersion = None,
  )

  object Deps {
    final val izumi_reflect = Library("dev.zio", "izumi-reflect", V.izumi_reflect, LibraryType.Auto)

    final val scalatest_all = Seq(
      // repeat `scalatest` dependencies, but exclude `scalatest-expectations`(2.13) and `scalatest_refspec`(sjs1_2.13)
      // because they're missing in `3.3.0-alpha.2` release
      Library("org.scalatest", "scalatest-core", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-diagrams", V.scalatest, LibraryType.Auto),
  //      Library("org.scalatest", "scalatest-expectations", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-featurespec", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-flatspec", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-freespec", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-funspec", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-funsuite", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-matchers-core", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-mustmatchers", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-propspec", V.scalatest, LibraryType.Auto),
  //      Library("org.scalatest", "scalatest-refspec", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-shouldmatchers", V.scalatest, LibraryType.Auto),
      Library("org.scalatest", "scalatest-wordspec", V.scalatest, LibraryType.Auto),

      // add scalatestplus scalacheck dependency because the versions have to match scalatest ones
      Library("org.scalatestplus", "scalacheck-1-18", V.scalatestplus_scalacheck, LibraryType.Auto),
    )

    final val cats_core = Library("org.typelevel", "cats-core", V.cats, LibraryType.Auto)
    final val cats_effect = Library("org.typelevel", "cats-effect", V.cats_effect, LibraryType.Auto)
    final val scalac_compat_annotation = Library("org.typelevel", "scalac-compat-annotation", V.scalac_compat_annotation, LibraryType.AutoJvm) in Scope.Provided.all.scalaVersion(
      ScalaVersionScope.AllScala2
    )
    final val cats_all = Seq(cats_core, cats_effect)
    final val cats_effect_laws = Library("org.typelevel", "cats-effect-laws", V.cats_effect, LibraryType.Auto)
    final val cats_effect_testkit = Library("org.typelevel", "cats-effect-testkit", V.cats_effect, LibraryType.Auto)

    final val circe_core = Library("io.circe", "circe-core", V.circe, LibraryType.Auto)
    final val circe_parser = Library("io.circe", "circe-parser", V.circe, LibraryType.Auto)
    final val circe_literal = Library("io.circe", "circe-literal", V.circe, LibraryType.Auto)
    final val circe_generic = Library("io.circe", "circe-generic", V.circe, LibraryType.Auto)
    final val circe_derivation_scala2 = Library("io.circe", "circe-derivation", V.circe_derivation, LibraryType.Auto)

    final val discipline = Library("org.typelevel", "discipline-core", V.discipline, LibraryType.Auto)
    final val discipline_scalatest = Library("org.typelevel", "discipline-scalatest", V.discipline_scalatest, LibraryType.Auto)

    final val pureconfig_core = Library("com.github.pureconfig", "pureconfig-core", V.pureconfig, LibraryType.Auto)
    final val pureconfig_magnolia = Library("com.github.pureconfig", "pureconfig-magnolia", V.pureconfig, LibraryType.Auto)
    final val magnolia = Library("com.softwaremill.magnolia1_2", "magnolia", V.magnolia, LibraryType.Auto)

    final val zio_core = Library("dev.zio", "zio", V.zio, LibraryType.Auto)
      .more(LibSetting.Raw("""excludeAll("dev.zio" %% "izumi-reflect")"""))
    final val zio_managed = Library("dev.zio", "zio-managed", V.zio, LibraryType.Auto)
      .more(LibSetting.Raw("""excludeAll("dev.zio" %% "izumi-reflect")"""))
    final val zio_all = Seq(zio_core)

    final val zio_interop_cats = Library("dev.zio", "zio-interop-cats", V.zio_interop_cats, LibraryType.Auto)
      .more(LibSetting.Raw("""excludeAll("dev.zio" %% "izumi-reflect")"""))

    final val zio_interop_tracer = Library("dev.zio", "zio-interop-tracer", V.zio_interop_cats, LibraryType.Auto)

  //    final val monix = Library("io.monix", "monix", V.monix, LibraryType.Auto)
  //    final val monix_bio = Library("io.monix", "monix-bio", V.monix_bio, LibraryType.Auto)
  //    final val monix_all = Seq(monix, monix_bio)
    // FIXME Disable monix due to lack of CE3 support as of now, see:
    //   https://github.com/monix/monix/issues/1502
    //   https://github.com/monix/monix/pull/1533
    final val monix_all = Seq.empty[Library]

    final val typesafe_config = Library("com.typesafe", "config", V.typesafe_config, LibraryType.Invariant)
  //    final val jawn = Library("org.typelevel", "jawn-parser", V.jawn, LibraryType.AutoJvm)

    final val scala_sbt = Library("org.scala-sbt", "sbt", Version.VExpr("sbtVersion.value"), LibraryType.Invariant)
    final val sbt_test_interface = Library("org.scala-sbt", "test-interface", V.sbt_test_interface, LibraryType.Invariant) in Scope.Compile.jvm
    final val scalajs_test_interface = Library("org.scala-js", "scalajs-test-interface_2.13", V.scalajs_test_interface, LibraryType.Invariant) in Scope.Compile.js
    final val native_test_interface = Library("org.scala-native", "test-interface", settings.scalaNativeVersion, LibraryType.Auto) in Scope.Compile.native
    final val scala_compiler = Library("org.scala-lang", "scala-compiler", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala3_compiler = Library("org.scala-lang", "scala3-compiler", Version.VExpr("scalaVersion.value"), LibraryType.AutoJvm) in Scope.Provided.all.scalaVersion(
      ScalaVersionScope.AllScala3
    )
    final val scala_library = Library("org.scala-lang", "scala-library", Version.VExpr("scalaVersion.value"), LibraryType.Invariant)
    final val scala_reflect = Library("org.scala-lang", "scala-reflect", Version.VExpr("scalaVersion.value"), LibraryType.Invariant) in Scope.Provided.all.scalaVersion(
      ScalaVersionScope.AllScala2
    )

    final val bytebuddy = Library("net.bytebuddy", "byte-buddy", V.bytebuddy, LibraryType.Invariant)

    final val projector = Library("org.typelevel", "kind-projector", V.kind_projector, LibraryType.Invariant)
      .more(LibSetting.Raw("cross CrossVersion.full"))

    final val fast_classpath_scanner = Library("io.github.classgraph", "classgraph", V.classgraph, LibraryType.Invariant)

    final val scala_java_time = Library("io.github.cquiroz", "scala-java-time", V.scala_java_time, LibraryType.Auto)
    final val scala_java_time_tzdb = Library("io.github.cquiroz", "scala-java-time-tzdb", V.scala_java_time, LibraryType.Auto)
    final val scalamock = Library("org.scalamock", "scalamock", V.scalamock, LibraryType.Auto)
    final val scalajs_macrotask_executor = Library("org.scala-js", "scala-js-macrotask-executor", V.scalajs_macrotask_executor, LibraryType.Auto)

    final val slf4j_api = Library("org.slf4j", "slf4j-api", V.slf4j, LibraryType.Invariant)
    final val slf4j_simple = Library("org.slf4j", "slf4j-simple", V.slf4j, LibraryType.Invariant)

    val doobie_all = Seq(
      Library("org.tpolecat", "doobie-core", V.doobie, LibraryType.Auto),
      Library("org.tpolecat", "doobie-postgres", V.doobie, LibraryType.Auto),
    )

    val docker_java_core = Library("com.github.docker-java", "docker-java-core", V.docker_java, LibraryType.Invariant)
    val docker_java_transport_zerodep = Library("com.github.docker-java", "docker-java-transport-zerodep", V.docker_java, LibraryType.Invariant)
    val commons_compress = Library("org.apache.commons", "commons-compress", V.commons_compress, LibraryType.Invariant)

    val javaXInject = Library("javax.inject", "javax.inject", "1", LibraryType.Invariant)

    val portable_scala_reflect = Library("org.portable-scala", "portable-scala-reflect", V.portable_scala_reflect, LibraryType.Auto)
      .more(LibSetting.Raw("cross CrossVersion.for3Use2_13"))
  }

  import Deps._

  // DON'T REMOVE, these variables are read from CI build (build.sh)
  final val scala213 = ScalaVersion("2.13.18")
  final val scala300 = ScalaVersion("3.9.0")
  final val scalaSbt2Plugin = ScalaVersion("3.8.4")

  object Groups {
    final val fundamentals = Set(Group("fundamentals"))
    final val distage = Set(Group("distage"))
    final val logstage = Set(Group("logstage"))
    final val docs = Set(Group("docs"))
    final val sbt = Set(Group("sbt"))
  }

  /**
    * Compile-time macros in this repo (`PortableResource.embedResources`, distage's `planCheck`)
    * enumerate the compile-time classpath, where a project's own test resources only appear once
    * `copyResources` has copied them into the class directory. Nothing orders that before
    * compilation, so without this the macros observe an incomplete classpath: `embedResources`
    * fails outright, `planCheck` silently resolves a same-named config from another project.
    *
    * `compileIncremental` and not `compile`: the compilation itself happens in the body of the
    * former, so only a dependency of the former is ordered before it. Applied to every library
    * artifact: `copyResources` is cheap, and any module may acquire such a macro in its tests.
    *
    * Not applicable to sbt plugin projects: for those, sbt's `resourceGenerators` write the
    * `sbt.autoplugins` descriptor from the *compiled* classes (`discoverSbtPluginNames` pulls
    * `compile` through a dynamic `flatMapTask`), so ordering `copyResources` before compilation
    * closes a cycle that sbt cannot detect and the build hangs forever in `Test / compile`.
    */
  private val testResourcesOnCompileClasspath: Seq[SettingDef] = Seq(
    SettingDef.RawSettingDef(
      """Test / compileIncremental := (Test / compileIncremental).dependsOn(Test / copyResources).value"""
    )
  )

  private def withTestResourcesOnCompileClasspath(artifacts: Seq[Artifact]): Seq[Artifact] = {
    artifacts.map(artifact => artifact.copy(settings = artifact.settings ++ testResourcesOnCompileClasspath))
  }

  private final val JvmRelease = "17"

  /**
    * Scala 3.9.0 crashes intermittently with `-Ybackend-parallelism` > 1 and `-explain-cyclic`: backend threads
    * race the main thread on shared compiler state, surfacing as `IndexOutOfBoundsException` in
    * `SymDenotations$BaseDataImpl`, `NoDenotation.owner` assertions and intact classpath jars read back as garbage
    * ("wrong magic number", "class file is broken"). Scala 3.7.4 does not exhibit it.
    *
    * Tracked in https://github.com/scala/scala3/issues/27209; restore the flag for Scala 3 once that is fixed.
    */
  private def withoutBackendParallelism(options: Seq[Const]): Seq[Const] = {
    val index = options.indexOf(Const.CString("-Ybackend-parallelism"))
    if (index < 0) options else options.patch(index, Nil, 2)
  }

  private def withJvmRelease(options: Seq[Const]): Seq[Const] = {
    options.filterNot {
      case Const.CString(option) => option.startsWith("-release:")
      case _ => false
    } :+ Const.CString(s"-release:$JvmRelease")
  }

  object Targets {
    val targetScala3 = Seq(scala300, scala213)

    private val jvmPlatform = PlatformEnv(
      platform = Platform.Jvm,
      language = targetScala3,
      settings = Seq.empty,
    )
    private val jsPlatform = PlatformEnv(
      platform = Platform.Js,
      language = targetScala3,
      settings = Seq(
        "coverageEnabled" := false,
        "scalaJSLinkerConfig" in (SettingScope.Project, Platform.Js) := "{ scalaJSLinkerConfig.value.withBatchMode(true).withModuleKind(ModuleKind.CommonJSModule) }".raw,
      ),
    )

    private val jvmPlatformSbt = PlatformEnv(
      platform = Platform.Jvm,
      language = Seq(scalaSbt2Plugin),
      settings = Seq(
        "coverageEnabled" := false
      ),
    )

    private val nativePlatform = PlatformEnv(
      platform = Platform.Native,
      language = targetScala3,
      settings = Seq(
        "coverageEnabled" := false,
        // The target test interface must match the Native plugin's host adapter.
        "libraryDependencySchemes" += """"org.scala-native" %% "test-interface_native0.5" % VersionScheme.Always""".raw,
      ),
    )

    final val cross = Seq(jvmPlatform, jsPlatform, nativePlatform)
    final val protocol = cross.map(_.copy(language = Seq(ScalaVersion("3.8.4"), scala213)))
    final val jvmJs = Seq(jvmPlatform, jsPlatform)
    final val jvm = Seq(jvmPlatform)
    final val js = Seq(jsPlatform)

    final val jvmSbt = Seq(jvmPlatformSbt)
  }

  object Projects {

    final val plugins = Plugins(
      Seq(Plugin("SitePreviewPlugin")),
    )

    object root {
      final val id = ArtifactId("izumi")
      final val plugins = Plugins(
        enabled = Seq(Plugin("SbtgenVerificationPlugin")),
      )

      final val outOfSource = Seq(
//        "target" := s"""baseDirectory.in(LocalProject("${Projects.root.id.value}")).value.toPath().resolve("target").resolve(baseDirectory.in(LocalProject("${Projects
//          .root.id.value}")).value.toPath().relativize(baseDirectory.value.toPath)).toFile""".raw
      )

      final val crossScalaSources = Defaults.CrossScalaPlusSources

      final val topLevelSettings = Seq()

      final val sharedAggSettings = outOfSource

      // Embedded resources close JAR filesystems shared by concurrent links (scala-native#2024).
      private final val MaxConcurrentNativeLinks = 1
      final val nativeLinkSettings = Seq(
        "concurrentRestrictions" in SettingScope.Raw("Global") +=
          s"Tags.limit(scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.NativeTags.Link, $MaxConcurrentNativeLinks)".raw
      )

      private final val javacOptions = Seq(
        "javacOptions" in SettingScope.Build ++= Seq(
          "-encoding",
          "UTF-8",
          "--release",
          JvmRelease,
          "-deprecation",
          "-parameters",
          "-Xlint:all",
          "-XDignore.symbol.file",
        )
      )

      final val rootSettings = Defaults.RootOptions.filterNot(_.name == "javacOptions") ++ javacOptions ++ Defaults.SbtMetaRootOptions ++ Seq(
//        "target" := s"""baseDirectory.in(LocalProject("${Projects.root.id.value}")).value.toPath().resolve("target").resolve("${Projects
//          .root.id.value}").toFile""".raw,
        "organization" in SettingScope.Build := "io.7mind.izumi",
        "publishTo" in SettingScope.Build :=
          """{
            |  // https://github.com/sbt/sbt/issues/8131
            |  if (isSnapshot.value) {
            |    Some(
            |      "central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
            |    )
            |  } else {
            |    localStaging.value
            |  }
            |}
            |""".stripMargin.raw,
        "credentials" in SettingScope.Build ++=
          """{
            |  val credTarget = Path.userHome / ".sbt" / "secrets" / "credentials.sonatype-new.properties"
            |  if (credTarget.exists) {
            |    Seq(Credentials(credTarget))
            |  } else {
            |    Seq.empty
            |  }
            |}""".stripMargin.raw,
        "credentials" in SettingScope.Build ++=
          """{
            |  val credTarget = Path.userHome / ".sbt" / "secrets" / "credentials.sonatype-nexus.properties"
            |  if (credTarget.exists) {
            |    Seq(Credentials(credTarget))
            |  } else {
            |    Seq.empty
            |  }
            |}""".stripMargin.raw,
        "credentials" in SettingScope.Build ++=
          """{
            |  val credTarget = file(".") / ".secrets" / "credentials.sonatype-nexus.properties"
            |  if (credTarget.exists) {
            |    Seq(Credentials(credTarget))
            |  } else {
            |    Seq.empty
            |  }
            |}""".stripMargin.raw,
        "homepage" in SettingScope.Build := """Some(url("https://izumi.7mind.io"))""".raw,
        "licenses" in SettingScope.Build := """Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))""".raw,
        "developers" in SettingScope.Build :=
          """List(
          Developer(id = "7mind", name = "Septimal Mind", url = url("https://github.com/7mind"), email = "team@7mind.io"),
        )""".raw,
        "scmInfo" in SettingScope.Build := """Some(ScmInfo(url("https://github.com/7mind/izumi"), "scm:git:https://github.com/7mind/izumi.git"))""".raw,
      )

      val scala2Wconf = Seq[Const](
        "-Wconf:msg=parameter.*x\\\\$4.in.anonymous.function.is.never.used:silent",
        "-Wconf:msg=constructor.modifiers.are.assumed.by.synthetic.*method:silent",
        "-Wconf:msg=package.object.inheritance:silent",
        "-Wconf:msg=not.a.valid.main.method:silent",
        "-Wconf:msg=has.a.main.method.with.parameter.type.Array:silent",
        "-Wconf:cat=lint-eta-sam:silent",
      )
      val scala3Wconf = Seq[Const](
        // enable all warnings except -Wtostring-interpolated, -Wshadow and -Wsafe-init which slows down compilation to a crawl
        "-Wenum-comment-discard",
        "-Wimplausible-patterns",
        "-Wnonunit-statement",
        // "-Wsafe-init",
        // "-Wshadow:all",
        // "-Wtostring-interpolated",
        "-WunstableInlineAccessors",
        "-Wunused:all",
        "-Wvalue-discard",
        //
        "-Wconf:any:verbose",
        "-Wconf:name=UnusedNonUnitValue:silent",
        "-Wconf:name=ValueDiscarding:silent",
        "-Wconf:msg=eta-expanded even though:silent", // disable harmful anti-SAM warning
        //
        "-Wconf:msg=Ignoring .this. qualifier:silent",
        "-Wconf:msg=.this. qualifier will be deprecated:silent",
        "-Wconf:msg=scala.compiletime.uninitialized:silent",
        "-Wconf:msg=`using` clause:silent",
        "-Wconf:msg=The syntax ..function:silent",
        "-Wconf:msg=method contains is not declared infix:silent",
        "-Wconf:msg=method in is not declared infix:silent",
      )

      def scala3Options(source: String): Seq[Const] = {
        withoutBackendParallelism(
          withJvmRelease(
            Seq[Const](
              s"-source:$source",
              "-Xkind-projector:underscores",
              // Scala 3.9.0 scans every classpath root, including sbt's synthesized JDK `rt.jar`, to suggest
              // imports for "not found"/"missing given" errors. On JDK 21+ that parse trips an inner-class
              // assertion (`javax.swing.RepaintManager$PaintManager`) and crashes the compiler instead of
              // reporting the error; the typecheck-expecting tests in distage-testkit-scalatest hit it
              // deterministically. Disabling the suggestions only loses the "did you mean to import" hints.
              //
              // Tracked in https://github.com/scala/scala3/issues/25451; drop this flag once that is fixed.
              "-Ximport-suggestion-timeout:0",
            ) ++ Defaults.Scala3Options
              .filterNot(x => x == ("-Ykind-projector:underscores": Const) || x == ("-Xkind-projector:underscores": Const))
              .filterNot(scala3Wconf.contains(_))
            ++ scala3Wconf
          )
        )
      }

      val wconfOverrides = Seq[Const](
        "-Wconf:cat=deprecation:warning",
        "-Wconf:msg=legacy-binding:silent",
        "-Wconf:msg=nowarn:silent",
      )

      final val sharedSettings = Defaults.SbtMetaSharedOptions ++ outOfSource ++ crossScalaSources ++ Seq(
      "testOptions" in SettingScope.Test += """Tests.Argument("-oDF")""".raw,
      // sbt 2.0.5+ closes the adhoc test ClassLoader once the test task completes. The ZIO and
      // cats-effect runtimes keep their worker threads alive past that point (ZIO's global
      // `Runtime.default` scheduler cannot be shut down at all), so the next class load on any of
      // them fails and the JVM drowns in `LinkageError`s with no sbt-level error: CI produced
      // 6351 of them from a run whose suites had all passed. Keeping the loader open leaks the
      // threads instead, which is the lesser evil until ZIO can close its scheduler, see
      // https://github.com/zio/zio/issues/10019 and https://github.com/zio/zio/pull/10926.
      "closeClassLoaders" := false,
      "scalacOptions" ++= Seq(
        SettingKey(Some(scala213), None) :=
          withJvmRelease(
            (Seq[Const]("-Wconf:any:error") ++ Defaults.Scala213Options ++ Seq[Const]("-Wunused:-synthetics")).filterNot(_ == ("-Xsource:3-cross": Const)) ++ scala2Wconf
          ),
        SettingKey(Some(scala300), None) := scala3Options("3.9"),
        SettingKey.Default := Const.EmptySeq,
      ),
      "scalacOptions" -= "-Wconf:any:warning",
      "scalacOptions" ++= wconfOverrides,
      "scalacOptions" in SettingScope.Raw("Compile / sbt.Keys.doc") -= "-Wconf:any:error",
      "scalacOptions" ++= Seq(
        """s"-Xmacro-settings:scalatest-version=${V.scalatest}"""".raw,
        """s"-Xmacro-settings:is-ci=${insideCI.value}"""".raw,
      ),
      "scalacOptions" ++= Seq(
        SettingKey(Some(scala213), Some(true)) := Seq(
          "-opt:l:inline",
          "-opt-inline-from:izumi.**",
        ),
        SettingKey.Default := Const.EmptySeq,
      ),
      "scalacOptions" ++= Seq(
        SettingKey(Some(scala213), None) := Seq(
          "-Xsource:3",
          "-Xmigration",
          "-Wconf:cat=scala3-migration:silent",
          "-Wconf:cat=other-migration:silent",
        ),
        SettingKey.Default := Const.EmptySeq,
      ),
      "publishArtifact" in SettingScope.Raw("Test / packageDoc") := false,
    )

    }

    object fundamentals {
      final val id = ArtifactId("fundamentals")
      final val basePath = Seq("fundamentals")

      final val basics = ArtifactId("fundamentals-basics")
      final val collections = ArtifactId("fundamentals-collections")
      final val platform = ArtifactId("fundamentals-platform")
      final val functoid = ArtifactId("fundamentals-functoid")
      final val language = ArtifactId("fundamentals-language")
      final val functional = ArtifactId("fundamentals-functional")
      final val bio = ArtifactId("fundamentals-bio")
      final val orphans = ArtifactId("fundamentals-orphans")
      final val assertions = ArtifactId("fundamentals-assertions")
      final val assertionsCats = ArtifactId("fundamentals-assertions-cats")
      final val assertionsBIO = ArtifactId("fundamentals-assertions-bio")
      final val testSupport = ArtifactId("fundamentals-test-support")
      final val platformTest = ArtifactId("fundamentals-platform-test")
      final val bioTest = ArtifactId("fundamentals-bio-test")
      final val collectionsTest = ArtifactId("fundamentals-collections-test")
      final val jsonCirceTest = ArtifactId("fundamentals-json-circe-test")
      final val languageTest = ArtifactId("fundamentals-language-test")

      final val typesafeConfig = ArtifactId("fundamentals-typesafe-config")
//      final val reflection = ArtifactId("fundamentals-reflection")
      final val jsonCirce = ArtifactId("fundamentals-json-circe")
//
//      final lazy val basics = Seq(
//        platform,
//        functoid,
//        collections,
//        functional,
//      ).map(_ in Scope.Runtime.all)
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
      final lazy val testProtocol = ArtifactId("distage-test-protocol")
      final lazy val testRunner = ArtifactId("distage-test-runner")
      final lazy val testkitRunner = ArtifactId("distage-testkit-runner")
      final lazy val testkitScalatest = ArtifactId("distage-testkit-scalatest")
      final lazy val testkitScalatestSbtModuleFilteringTest = ArtifactId("distage-testkit-scalatest-sbt-module-filtering-test")
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
      final val id = ArtifactId("docs")
      final val basePath = Seq("doc")

      final lazy val microsite = ArtifactId("microsite")
    }

    object sbtplugins {
      final val id = ArtifactId("sbt-plugins")
      final val basePath = Seq("sbt-plugins")

      final val sbt2PluginTarget = "2.0.9"

      final val settings = Seq(
        "sbtPlugin" := true,
        "sbtPluginPublishLegacyMavenStyle" := false,
        SettingDef.RawSettingDef(s"""crossScalaVersions := Seq("${scalaSbt2Plugin.value}")"""),
        SettingDef.RawSettingDef("""scalaVersion := crossScalaVersions.value.head"""),
        "scalacOptions" ++= Seq(
          SettingKey(Some(scalaSbt2Plugin), None) := root.scala3Options("3.8") ++ root.wconfOverrides,
          SettingKey.Default := Const.EmptySeq,
        ),
        "scalacOptions" -= "-Wconf:any:warning",
        SettingDef.RawSettingDef(s"""pluginCrossBuild / sbtVersion := "$sbt2PluginTarget""""),
      )

      final lazy val izumi_deps = ArtifactId("sbt-izumi-deps")
      final lazy val distage_testkit = ArtifactId("sbt-distage-testkit")
    }

  }

  final val forkTests = Seq(
    "fork" in (SettingScope.Test, Platform.Jvm) := true
  )

  final val assertionFixtureSettings = Seq(
    "libraryDependencies" ~= """(_.filterNot(m => Set("org.scalatest", "org.scalactic", "org.scalatestplus").contains(m.organization)))""".raw,
    "testOptions" in SettingScope.Test := Const.EmptySeq,
    "testFull" in SettingScope.Test := """Def.uncached { (Test / run).toTask("").value; sbt.protocol.testing.TestResult.Passed }""".raw,
    "test" in SettingScope.Test := """(Test / testFull).value""".raw,
    "scalaJSUseMainModuleInitializer" in (SettingScope.Test, Platform.Js) := true,
    "scalaJSUseTestModuleInitializer" in (SettingScope.Test, Platform.Js) := false,
  )

  private def fundamentalsTestSettings(targetName: String): Seq[SettingDef] = Seq(
    "skip" in SettingScope.Raw("publish") := true,
    "testOptions" in SettingScope.Test := """Seq(Tests.Argument(new TestFramework("org.scalatest.tools.Framework"), "-oDF"))""".raw,
    "testFrameworks" in (SettingScope.Test, Platform.Jvm) :=
      """Seq(new TestFramework("org.scalatest.tools.Framework"), new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"))""".raw,
    "testOptions" in (SettingScope.Test, Platform.Jvm) +=
      s"""Tests.Argument(new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"), "--build-id", "izumi-repository", "--target-id", "$targetName-jvm", "--catalogue-id", "$targetName-catalogue")""".raw,
  )

  final lazy val fundamentals = Aggregate(
    name = Projects.fundamentals.id,
    artifacts = withTestResourcesOnCompileClasspath(Seq(
      Artifact(
        name = Projects.fundamentals.basics,
        libs = Seq.empty,
        depends = Seq.empty,
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.functional,
        libs = Seq.empty,
        depends = Seq(Projects.fundamentals.basics),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.collections,
        libs = Seq.empty,
        depends = Seq(
          Projects.fundamentals.basics,
          Projects.fundamentals.functional,
        ),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.assertions,
        libs = Seq(scala_reflect),
        depends = Seq.empty,
        settings = assertionFixtureSettings ++ Seq(
          "mainClass" in SettingScope.Test :=
            """{
              |  val options = (Test / scalacOptions).value
              |  val pointOnly = options.contains("-Yrangepos:false")
              |  Some(if (pointOnly) "izumi.fundamentals.assertions.AssertionFixturesWithoutRanges" else "izumi.fundamentals.assertions.AssertionFixtures")
              |}""".stripMargin.raw,
        ),
      ),
      Artifact(
        name = Projects.fundamentals.assertionsCats,
        libs = Seq(cats_effect),
        depends = Seq(Projects.fundamentals.assertions),
        settings = assertionFixtureSettings ++ Seq(
          "mainClass" in SettingScope.Test := "Some(\"izumi.fundamentals.assertions.cats.CatsAssertionFixtures\")".raw,
        ),
      ),
      Artifact(
        name = Projects.fundamentals.assertionsBIO,
        libs = Seq(zio_core, izumi_reflect).map(_ in Scope.Test.all) ++ Seq(scala_java_time in Scope.Test.js, scala_java_time in Scope.Test.native),
        depends = Seq(Projects.fundamentals.assertions, Projects.fundamentals.bio),
        settings = assertionFixtureSettings ++ Seq(
          "mainClass" in SettingScope.Test := "Some(\"izumi.fundamentals.assertions.bio.BIOAssertionFixtures\")".raw,
        ),
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.fundamentals.orphans,
        libs = allMonadsOptional ++ Seq(zio_interop_cats in Scope.Optional.all),
        depends = Seq(Projects.fundamentals.basics),
        settings = Seq.empty,
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.fundamentals.language,
        libs = Seq(
          scala_reflect,
          scala3_compiler,
        ),
        depends = Seq(
          Projects.fundamentals.basics,
        ),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.platform,
        libs = Seq(
          izumi_reflect in Scope.Compile.all,
          scala_reflect,
          fast_classpath_scanner in Scope.Provided.all,
          scala_java_time in Scope.Compile.native,
          scalajs_macrotask_executor in Scope.Compile.js
        ),
        depends = Seq(
          Projects.fundamentals.functional,
          Projects.fundamentals.basics,
          Projects.fundamentals.language in Scope.Compile.all,
          Projects.fundamentals.collections in Scope.Compile.all,
//          Projects.fundamentals.reflection in Scope.Compile.all,
        ),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.functoid,
        libs = Seq(
          izumi_reflect in Scope.Compile.all,
          scala_reflect,
        ),
        depends = Seq(
          Projects.fundamentals.platform,
          Projects.fundamentals.language in Scope.Compile.all,
          Projects.fundamentals.collections in Scope.Compile.all,
          //          Projects.fundamentals.reflection in Scope.Compile.all,
        ),
      ),
      Artifact(
        name = Projects.fundamentals.jsonCirce,
        libs = Seq(
          scala_reflect,
          circe_core in Scope.Compile.all,
          circe_generic in Scope.Compile.all.scalaVersion(ScalaVersionScope.AllScala3),
        ),
        depends = Seq(Projects.fundamentals.platform),
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.fundamentals.testSupport,
        libs = scalatest_all.flatMap(library => Seq(library in Scope.Compile.js, library in Scope.Compile.native)),
        depends = Seq(Projects.distage.testRunner in Scope.Compile.jvm),
        settings = Seq("skip" in SettingScope.Raw("publish") := true),
      ),
      Artifact(
        name = Projects.fundamentals.platformTest,
        libs = Seq(scala_reflect, fast_classpath_scanner in Scope.Provided.all, scala_java_time_tzdb in Scope.Test.native),
        depends = Seq(Projects.fundamentals.platform, Projects.fundamentals.testSupport).map(_ in Scope.Test.all),
        settings = fundamentalsTestSettings("fundamentals-platform-test") ++ testResourcesOnCompileClasspath,
      ),
      Artifact(
        name = Projects.fundamentals.collectionsTest,
        libs = Seq(scala_reflect),
        depends = Seq(Projects.fundamentals.collections, Projects.fundamentals.testSupport).map(_ in Scope.Test.all),
        settings = fundamentalsTestSettings("fundamentals-collections-test"),
      ),
      Artifact(
        name = Projects.fundamentals.languageTest,
        libs = Seq(scala_reflect),
        depends = Seq(Projects.fundamentals.language, Projects.fundamentals.testSupport).map(_ in Scope.Test.all),
        settings = fundamentalsTestSettings("fundamentals-language-test"),
      ),
      Artifact(
        name = Projects.fundamentals.jsonCirceTest,
        libs = Seq(scala_reflect) ++ Seq(
          circe_derivation_scala2 in Scope.Test.jvm.scalaVersion(ScalaVersionScope.AllScala2),
          circe_derivation_scala2 in Scope.Test.js.scalaVersion(ScalaVersionScope.AllScala2),
          circe_generic in Scope.Test.all.scalaVersion(ScalaVersionScope.AllScala2),
          circe_literal in Scope.Test.all,
        ),
        depends = Seq(Projects.fundamentals.jsonCirce, Projects.fundamentals.testSupport).map(_ in Scope.Test.all),
        settings = fundamentalsTestSettings("fundamentals-json-circe-test") ++ Seq(
          "unmanagedSourceDirectories" in (SettingScope.Test, Platform.Jvm) +=
            """file("fundamentals/fundamentals-json-circe-test/src/test/scala-derivation").getAbsoluteFile""".raw,
          "unmanagedSourceDirectories" in (SettingScope.Test, Platform.Js) +=
            """file("fundamentals/fundamentals-json-circe-test/src/test/scala-derivation").getAbsoluteFile""".raw,
          "unmanagedSourceDirectories" in (SettingScope.Test, Platform.Native) ++=
            """{ if (scalaVersion.value.startsWith("3.")) Seq(file("fundamentals/fundamentals-json-circe-test/src/test/scala-derivation").getAbsoluteFile) else Seq.empty }""".raw,
          //        workaround for:
          //        java.lang.RuntimeException: found version conflict(s) in library dependencies; some are suspected to be binary incompatible:
          //          +- io.circe:circe-derivation_2.13:0.13.0-M5           (depends on 0.13.0)
          "libraryDependencySchemes" += s""""${circe_core.group}" %% "${circe_core.artifact}" % VersionScheme.Always""".raw,
          "libraryDependencySchemes" += s""""${circe_core.group}" %% "${circe_core.artifact}_sjs1" % VersionScheme.Always""".raw,
        ),
      ),
      Artifact(
        name = Projects.fundamentals.bioTest,
        libs = Seq(scala_reflect, scalac_compat_annotation) ++ allMonadsTest ++
          Seq(cats_effect_laws, cats_effect_testkit, discipline, zio_managed, zio_interop_cats).map(_ in Scope.Test.all) ++
          Seq(scala_java_time in Scope.Test.js, scala_java_time in Scope.Test.native),
        depends = Seq(Projects.fundamentals.bio, Projects.fundamentals.testSupport).map(_ in Scope.Test.all),
        settings = fundamentalsTestSettings("fundamentals-bio-test"),
        platforms = Targets.cross,
      ),
//      Artifact(
//        name = Projects.fundamentals.reflection,
//        libs = Seq(izumi_reflect in Scope.Compile.all, scala_reflect),
//        depends = Seq(
//          Projects.fundamentals.functional,
//          Projects.fundamentals.language,
//        ),
//        settings = Seq.empty,
//      ),
      Artifact(
        name = Projects.fundamentals.bio,
        libs = allMonadsOptional ++
          Seq(scalac_compat_annotation) ++
          Seq(zio_managed in Scope.Optional.all) ++
          Seq(zio_interop_tracer in Scope.Compile.all),
        depends = Seq(
          Projects.fundamentals.language,
          Projects.fundamentals.orphans,
          Projects.fundamentals.collections,
          Projects.fundamentals.basics,
        ),
        settings = Seq.empty,
        platforms = Targets.cross,
      ),
    )),
    pathPrefix = Projects.fundamentals.basePath,
    groups = Groups.fundamentals,
    defaultPlatforms = Targets.cross,
  )

  final val allCatsOptional = cats_all.map(_ in Scope.Optional.all)
  final val allZioOptional = (zio_all ++ Seq(izumi_reflect)).map(_ in Scope.Optional.all)
  final val allMonads = cats_all ++ zio_all ++ Seq(zio_interop_cats) ++ Seq(izumi_reflect) ++ monix_all
  final val allMonadsOptional = allCatsOptional ++ allZioOptional ++ monix_all.map(_ in Scope.Optional.all)
  final val allMonadsTest = (cats_all ++ monix_all ++ zio_all ++ Seq(izumi_reflect)).map(_ in Scope.Test.all)

  final lazy val distage = Aggregate(
    name = Projects.distage.id,
    artifacts = withTestResourcesOnCompileClasspath(Seq(
      Artifact(
        name = Projects.distage.testProtocol,
        libs = Seq(circe_core, circe_parser),
        depends = Seq.empty,
        platforms = Targets.protocol,
        settings = assertionFixtureSettings ++ Seq(
          "scalacOptions" ++= Seq(
            SettingKey(Some(scalaSbt2Plugin), None) := Projects.root.scala3Options("3.8") ++ Projects.root.wconfOverrides,
            SettingKey.Default := Const.EmptySeq,
          ),
          "mainClass" in SettingScope.Test := "Some(\"izumi.distage.testkit.protocol.ProtocolFixtures\")".raw,
        ),
      ),
      Artifact(
        name = Projects.distage.testRunner,
        libs = Seq(scala_reflect, sbt_test_interface, scalajs_test_interface, native_test_interface),
        depends = Seq(Projects.fundamentals.assertions, Projects.distage.testProtocol),
        platforms = Targets.cross,
        settings = assertionFixtureSettings ++ Seq(
          "unmanagedSourceDirectories" in (SettingScope.Compile, Platform.Js) +=
            """file("distage/distage-test-runner/src/main/scala-target").getAbsoluteFile""".raw,
          "unmanagedSourceDirectories" in (SettingScope.Compile, Platform.Native) +=
            """file("distage/distage-test-runner/src/main/scala-target").getAbsoluteFile""".raw,
          "mainClass" in SettingScope.Test := "Some(\"izumi.distage.testkit.runner.BaseRunnerFixtures\")".raw,
        ),
      ),
      Artifact(
        name = Projects.distage.coreApi,
        libs = allCatsOptional ++ allZioOptional ++ allMonadsTest ++ Seq(scala_reflect) ++ Seq(zio_managed in Scope.Optional.all),
        depends = Seq(
//          Projects.fundamentals.reflection,
          Projects.fundamentals.platform,
          Projects.fundamentals.functoid,
          Projects.fundamentals.bio,
        ).map(_ in Scope.Compile.all),
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.distage.proxyBytebuddy,
        libs = Seq(bytebuddy in Scope.Compile.jvm),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.frameworkApi,
        libs = Seq(scala_reflect),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all),
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.distage.core,
        libs = allMonadsOptional ++ Seq(
          zio_interop_cats in Scope.Optional.all
        ) ++ Seq(
          scala_java_time in Scope.Test.js,
          scala_java_time in Scope.Test.native,
          javaXInject in Scope.Test.all,
        ),
        depends = Seq(
          Projects.distage.coreApi in Scope.Compile.all,
          Projects.distage.proxyBytebuddy in Scope.Compile.jvm,
          Projects.fundamentals.platform in Scope.Compile.all,
          Projects.fundamentals.platformTest tin Scope.Test.all,
        ),
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.distage.config,
        libs = Seq(
          pureconfig_core in Scope.Compile.jvm,
          pureconfig_magnolia in Scope.Compile.jvm.scalaVersion(ScalaVersionScope.Versions(scala213)),
          magnolia in Scope.Compile.jvm.scalaVersion(ScalaVersionScope.AllScala2),
        ) ++ Seq(
          circe_core in Scope.Compile.js,
          circe_generic in Scope.Compile.js,
          circe_core in Scope.Compile.native,
          circe_generic in Scope.Compile.native,
        ) ++ Seq(
          circe_parser in Scope.Test.js,
          scala_java_time in Scope.Test.js,
          circe_parser in Scope.Test.native,
          scala_java_time in Scope.Test.native,
        ) ++ Seq(scala_reflect),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all),
        platforms = Targets.cross,
        settings = Seq.empty,
      ),
      Artifact(
        name = Projects.distage.extensionLogstage,
        libs = Seq(
          cats_effect in Scope.Test.all,
          zio_core in Scope.Test.all
        ),
        depends = Seq(Projects.distage.config, Projects.distage.coreApi).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core).map(_ in Scope.Test.all) ++
          Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.distage.plugins,
        libs = Seq(fast_classpath_scanner in Scope.Compile.all) ++ Seq(scala_reflect) ++
          Seq( /* for ZIOResourcesZManagedTestJvm */ zio_managed, zio_interop_cats, cats_effect, javaXInject).map(_ in Scope.Test.jvm),
        depends = Seq(Projects.distage.coreApi).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.config, Projects.logstage.core).map(_ in Scope.Test.all) ++
          Seq( /* for ZIOResourcesZManagedTestJvm */ Projects.fundamentals.platformTest tin Scope.Test.jvm),
        platforms = Targets.cross,
      ),
      Artifact(
        name = Projects.distage.framework,
        libs = allCatsOptional ++ allMonadsTest ++ Seq(scala_reflect) ++ Seq(scala3_compiler) ++ Seq(
          circe_parser in Scope.Test.all,
          circe_parser in Scope.Compile.js,
          circe_parser in Scope.Compile.native,
        ),
        depends = Seq(Projects.distage.extensionLogstage, Projects.logstage.renderingCirce).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.frameworkApi, Projects.distage.plugins, Projects.distage.config).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.plugins).map(_ tin Scope.Compile.all),
        platforms = Targets.cross,
        settings = Seq(
          "nativeConfig" in (SettingScope.Test, Platform.Native) := """nativeConfig.value.withEmbedResources(true)""".raw,
        ),
      ),
      Artifact(
        name = Projects.distage.docker,
        libs = allMonadsTest ++ Seq(docker_java_core, docker_java_transport_zerodep, commons_compress).map(_ in Scope.Compile.jvm),
        depends = Seq(Projects.distage.core, Projects.distage.config, Projects.distage.frameworkApi, Projects.distage.extensionLogstage).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.testkitScalatest in Scope.Test.all),
        platforms = Targets.jvm,
      ),
      Artifact(
        name = Projects.distage.testkitCore,
        libs = Seq.empty,
        depends = Seq(Projects.distage.framework).map(_ in Scope.Compile.all),
        platforms = Targets.cross,
        settings = assertionFixtureSettings ++ Seq(
          "mainClass" in SettingScope.Test := "Some(\"izumi.distage.testkit.spec.SessionEnvironmentFixtures\")".raw,
          "mainClass" in (SettingScope.Test, Platform.Native) := "Some(\"izumi.distage.testkit.spec.NativeTestkitFixtures\")".raw,
          "nativeConfig" in (SettingScope.Test, Platform.Native) := """nativeConfig.value.withEmbedResources(true)""".raw,
        ),
      ),
      Artifact(
        name = Projects.distage.testkitRunner,
        libs = Seq(zio_core in Scope.Optional.all, cats_effect in Scope.Test.all),
        depends = Seq(Projects.distage.testkitCore, Projects.distage.testRunner).map(_ in Scope.Compile.all) ++
          Seq(Projects.fundamentals.assertionsCats, Projects.fundamentals.assertionsBIO).map(_ in Scope.Test.all),
        platforms = Targets.cross,
        settings = assertionFixtureSettings ++ Seq(
          "mainClass" in SettingScope.Test := "Some(\"izumi.distage.testkit.runner.di.DistageProviderFixtures\")".raw,
        ),
      ),
      Artifact(
        name = Projects.distage.testkitScalatest,
        libs = allMonadsOptional ++ Seq(
          scalamock in Scope.Test.all,
          portable_scala_reflect in Scope.Compile.js,
        ) ++ scalatest_all.map(_ in Scope.Compile.all),
        depends = Seq(Projects.distage.testkitCore, Projects.fundamentals.assertions).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.core, Projects.distage.plugins).map(_ in Scope.Compile.all) ++
          Seq(Projects.distage.framework).map(_ tin Scope.Compile.all),
        platforms = Targets.jvmJs,
      ),
      Artifact(
        name = Projects.distage.testkitScalatestSbtModuleFilteringTest,
        libs = Nil,
        depends = Seq(
          Projects.distage.testkitScalatest tin Scope.Test.all
        ),
        platforms = Targets.jvm,
        settings = Seq(
          "skip" in SettingScope.Raw("publish") := true
        ),
      ),
    )),
    pathPrefix = Projects.distage.basePath,
    defaultPlatforms = Targets.jvmJs,
    groups = Groups.distage,
  )

  final lazy val logstage = Aggregate(
    name = Projects.logstage.id,
    artifacts = withTestResourcesOnCompileClasspath(Seq(
      Artifact(
        name = Projects.logstage.core,
        libs = Seq(scala_reflect) ++
          allCatsOptional ++ allZioOptional ++
          Seq(scala_java_time in Scope.Compile.js, scala_java_time in Scope.Compile.native),
        depends = Seq(
          Projects.fundamentals.bio,
          Projects.fundamentals.platform,
        ).map(_ in Scope.Compile.all),
      ),
      Artifact(
        name = Projects.logstage.renderingCirce,
        libs = Seq(
          circe_core in Scope.Compile.all,
          circe_parser in Scope.Test.all,
          circe_literal in Scope.Test.all,
          circe_generic in Scope.Test.all,
          cats_effect in Scope.Test.all,
          zio_core in Scope.Test.all,
        ),
        depends = Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
      ),
      Artifact(
        name = Projects.logstage.adapterSlf4j,
        libs = Seq(slf4j_api in Scope.Compile.all),
        depends = Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
        settings = Seq(
          "compileOrder" in SettingScope.Compile := "CompileOrder.Mixed".raw,
          "compileOrder" in SettingScope.Test := "CompileOrder.Mixed".raw,
          "classLoaderLayeringStrategy" in SettingScope.Test := "ClassLoaderLayeringStrategy.Flat".raw,
        ),
      ),
      Artifact(
        name = Projects.logstage.sinkSlf4j,
        libs = Seq(slf4j_api in Scope.Compile.all, slf4j_simple in Scope.Test.jvm),
        depends = Seq(Projects.logstage.core).map(_ tin Scope.Compile.all),
        platforms = Targets.jvm,
      ),
    )),
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
        libs = (allMonads ++ doobie_all).map(_ in Scope.Compile.all) ++
          Seq(circe_generic in Scope.Compile.all) ++
          Seq(zio_managed in Scope.Compile.all),
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
            (s => s"${DocKeys.snapshotSitePrefix}/$s")
          } else {
            identity
          }}""".raw,
          "previewFixedPort" := "Some(9999)".raw,
          "gitRemoteRepo" := "git@github.com:7mind/izumi-microsite.git",
          "mdocIn" := """baseDirectory.value / "src/main/tut"""".raw,
          "sourceDirectory" in SettingScope.Raw("(Compile / paradox)") := "mdocOut.value".raw,
          "mdocExtraArguments" ++= Seq(" --no-link-hygiene"),
          "version" in SettingScope.Raw("(Compile / paradox)") := "version.value".raw,
          // `sbt-paradox-material-theme` inlined, see `project/ParadoxMaterialTheme.scala`
          SettingDef.RawSettingDef("paradoxTheme := Some(ParadoxMaterialTheme.artifact)"),
          SettingDef.RawSettingDef("Compile / paradoxProperties ++= ParadoxMaterialTheme.properties(IzumiSite.materialTheme)"),
          SettingDef.RawSettingDef("""Compile / paradox / mappings += Def.uncached {
            val conv = fileConverter.value
            val (file, path) = ParadoxMaterialTheme.searchIndexMapping.value
            conv.toVirtualFile(file.toPath) -> path
          }"""),
          SettingDef.RawSettingDef("addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName)"),
          // Resolves `#member` fragments of API links to the ids scaladoc generated, see
          // `project/ScaladocAnchors.scala`. Must follow `addMappingsToSiteDir` above: that one
          // appends the API pages with `++=`, and the resolver needs them in the previous value.
          "mappings" in SettingScope.Raw("SitePlugin.autoImport.makeSite") :=
            """Def.uncached {
            val conv = fileConverter.value
            val siteMappings = (SitePlugin.autoImport.makeSite / mappings)
              .dependsOn(mdoc.toTask(" "))
              .value
              .map { case (ref, path) => conv.toPath(ref).toFile -> path }
            ScaladocAnchors
              .resolve(siteMappings, (ScalaUnidoc / siteSubdirName).value, target.value / "scaladoc-anchors", streams.value.log)
              .mappings
              .map { case (file, path) => conv.toVirtualFile(file.toPath) -> path }
          }""".raw,
          SettingDef.RawSettingDef(
            "ScalaUnidoc / unidoc / unidocProjectFilter := inAggregates(`fundamentals-jvm`, transitive = true) || inAggregates(`distage-jvm`, transitive = true) || inAggregates(`logstage-jvm`, transitive = true)"
          ),
          // Overlay src/main/paradox-overlay/ on the resolved paradox-material-theme
          // directory. We hook on `paradoxTemplate` (the task that constructs the
          // StringTemplate PageTemplate) rather than `paradoxThemeDirectory` (whose
          // self-modification would create a task cycle). When this fires, the theme
          // dir is already populated from the webjar; we copy our overrides on top
          // before instantiating the template, so e.g. our customized page.st replaces
          // the upstream one. Used to inject an inline <head> script that flips the
          // dark stylesheet's media= attribute synchronously before paint, eliminating
          // FOUC for repeat-visit light-mode users and providing a noscript
          // prefers-color-scheme fallback declaratively on the <link>.
          SettingDef.RawSettingDef("""Compile / paradoxTemplate := Def.uncached {
            val themeDir = (Compile / paradoxThemeDirectory).value
            val overlay = baseDirectory.value / "src/main/paradox-overlay"
            if (overlay.isDirectory) IO.copyDirectory(overlay, themeDir, overwrite = true)
            new com.lightbend.paradox.template.PageTemplate(themeDir, (Compile / paradoxDefaultTemplateName).value)
          }"""),
          "siteSubdirName" in SettingScope.Raw("ScalaUnidoc") := """DocKeys.prefix.value("api")""".raw,
          "siteSubdirName" in SettingScope.Raw("Paradox") := """DocKeys.prefix.value("")""".raw,
          SettingDef.RawSettingDef("""paradoxProperties ++= Def.uncached(Map(
            "scaladoc.izumi.base_url" -> s"/${DocKeys.prefix.value("api")}",
            "scaladoc.base_url" -> s"/${DocKeys.prefix.value("api")}",
            "izumi.version" -> version.value,
            "kindprojector.version" -> V.kind_projector,
          ))"""),
          SettingDef.RawSettingDef(
            """ghpagesCleanSite / excludeFilter := {
            val publishesSnapshot = isSnapshot.value
            new FileFilter {
              def accept(f: File): Boolean = {
                DocKeys.preservedSiteFiles.contains(f.getName) || ((f.getName == DocKeys.snapshotSiteRoot) != publishesSnapshot)
              }
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
        name = Projects.sbtplugins.distage_testkit,
        libs = Seq(bytebuddy in Scope.Compile.jvm),
        depends = Seq(Projects.distage.testProtocol in Scope.Compile.jvm),
        settings = Projects.sbtplugins.settings ++ Seq(
          "crossScalaVersions" := Seq("3.8.4"),
          "scalaVersion" := "crossScalaVersions.value.head".raw,
          "sbtVersion" in SettingScope.Raw("pluginCrossBuild") := "2.0.9",
          "libraryDependencies" ~= """(_.filterNot(m => Set("org.scalatest", "org.scalactic", "org.scalatestplus").contains(m.organization)))""".raw,
        ),
        plugins = Plugins(
          enabled = Seq.empty,
          disabled = Seq(Plugin("ScoverageSbtPlugin")),
        ),
      ),
      Artifact(
        name = Projects.sbtplugins.izumi_deps,
        libs = Seq.empty,
        depends = Seq.empty,
        settings = Projects.sbtplugins.settings ++ Seq(
          SettingDef.RawSettingDef("""withBuildInfo("izumi.sbt.deps", "Izumi")"""),
          """SettingKey[Boolean]("ide-skip-project")""" := true,
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
    ) ++ scalatest_all.map(_ in Scope.Test.all),
    rootPlugins = Projects.root.plugins,
    globalPlugins = Projects.plugins,
    appendPlugins = Defaults.SbtGenPlugins ++ Seq(
      SbtPlugin("com.github.sbt", "sbt-pgp", PV.sbt_pgp),
      SbtPlugin("org.scoverage", "sbt-scoverage", PV.sbt_scoverage),
      SbtPlugin("com.github.sbt", "sbt-unidoc", PV.sbt_unidoc),
      SbtPlugin("com.github.sbt", "sbt-site", PV.sbt_site),
      SbtPlugin("com.github.sbt", "sbt-site-paradox", PV.sbt_site),
      SbtPlugin("com.github.sbt", "sbt-ghpages", PV.sbt_ghpages),
      SbtPlugin("com.lightbend.paradox", "sbt-paradox", PV.sbt_paradox),
      SbtPlugin("com.lightbend.paradox", "sbt-paradox-theme", PV.sbt_paradox),
      SbtPlugin("org.scalameta", "sbt-mdoc", PV.sbt_mdoc),
    ),
  )
}
