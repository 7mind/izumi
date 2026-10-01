from pathlib import Path
here = Path(__file__).resolve().parent
modules = {
 'basics': ('fundamentals/fundamentals-basics', []),
 'functional': ('fundamentals/fundamentals-functional', ['basics']),
 'collections': ('fundamentals/fundamentals-collections', ['basics','functional']),
 'literals': ('fundamentals/fundamentals-literals', ['basics']),
 'orphans': ('fundamentals/fundamentals-orphans', ['basics']),
 'language': ('fundamentals/fundamentals-language', ['basics','literals']),
 'platform': ('fundamentals/fundamentals-platform', ['functional','basics','language','collections']),
 'functoid': ('fundamentals/fundamentals-functoid', ['platform','language','collections']),
 'bio': ('fundamentals/fundamentals-bio', ['language','orphans','collections','basics']),
 'coreApi': ('distage/distage-core-api', ['platform','functoid','bio']),
 'core': ('distage/distage-core', ['coreApi']),
 'frameworkApi': ('distage/distage-framework-api', ['coreApi']),
 'config': ('distage/distage-extension-config', ['coreApi']),
 'plugins': ('distage/distage-extension-plugins', ['coreApi']),
 'logCore': ('logstage/logstage-core', ['bio','platform']),
 'logCirce': ('logstage/logstage-rendering-circe', ['logCore']),
 'extensionLog': ('distage/distage-extension-logstage', ['config','coreApi','logCore']),
 'framework': ('distage/distage-framework', ['extensionLog','logCirce','core','frameworkApi','plugins','config']),
 'testkit': ('distage/distage-testkit-core', ['framework']),
}
header = '''import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "io.7mind.izumi.spike"
ThisBuild / version := "0.0.0-portability"
lazy val repo = file("../../../../../..").getCanonicalFile
// native05: Native 0.5 artifacts for Cats Effect 3.7.1, stand-ins for the unpublished ZIO interop artifacts,
// mixed JVM/JS platform sources, the CompletionStage adapter, and the repository's semantic Scala 3 flags.
val native05 = sys.props.contains("spike.native05")
val nativeDeps = Def.setting { Seq(
  "dev.zio" %%% "izumi-reflect" % "3.0.8",
  "dev.zio" %%% "zio" % "2.1.24",
  "dev.zio" %%% "zio-managed" % "2.1.24",
  "org.typelevel" %%% "cats-core" % "2.13.0",
) ++ (if (native05) Seq(
  "org.typelevel" %%% "cats-effect" % "3.7.1"
) else Seq(
  (if (sys.props.contains("spike.compileOnly")) "dev.zio" %% "zio-interop-cats" % "23.1.0.5" % Provided else "dev.zio" %%% "zio-interop-cats" % "23.1.0.5"),
  (if (sys.props.contains("spike.compileOnly")) "dev.zio" %% "zio-interop-tracer" % "23.1.0.5" % Provided else "dev.zio" %%% "zio-interop-tracer" % "23.1.0.5"),
  (if (sys.props.contains("spike.compileOnly")) "org.typelevel" %% "cats-effect" % "3.6.3" % Provided else "org.typelevel" %%% "cats-effect" % "3.6.3"),
)) ++ Seq(
  "io.circe" %%% "circe-core" % "0.14.14",
  "io.circe" %%% "circe-generic" % "0.14.14",
  "io.circe" %%% "circe-parser" % "0.14.14",
  "org.scala-lang.modules" %%% "scala-collection-compat" % "2.13.0"
) }
def module(id: String, path: String) = Project(id, file("modules/" + id)).enablePlugins(ScalaNativePlugin).settings(
  libraryDependencies ++= nativeDeps.value,
  libraryDependencies += "io.github.classgraph" % "classgraph" % "4.8.181" % Provided,
  libraryDependencies ++= (if (scalaVersion.value.startsWith("2.")) Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided, compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full)) else Seq("org.scala-lang" %% "scala3-compiler" % scalaVersion.value % Provided)),
  Compile / unmanagedSourceDirectories := {
    val sv = scalaBinaryVersion.value
    val jvmFiles = Set("fundamentals/fundamentals-platform", "fundamentals/fundamentals-bio", "logstage/logstage-core")
    val platformDir = if ((native05 || sys.props.contains("spike.mixedPlatform")) && jvmFiles(path)) ".jvm" else ".js"
    val roots = Seq(repo / path, repo / path / platformDir)
    (if ((native05 || sys.props.contains("spike.nativeAdapter")) && id == "bio") Seq(file("native-adapter/bio").getCanonicalFile) else Nil) ++
    (if (native05 && Set("bio", "core")(id)) Seq(file("native-standins/" + id).getCanonicalFile) else Nil) ++
    (if (native05 && Set("platform", "bio")(id)) Seq(file("native-platform/" + id).getCanonicalFile) else Nil) ++ roots.flatMap(r => Seq(r / "src/main/scala", r / ("src/main/scala-" + (if (sv == "3") "3" else "2")), r / "src/main/scala-2.12+", r / "src/main/scala-2.13+")) ++ (if ((native05 || sys.props.contains("spike.catsJvmAdapter")) && id == "core") Seq(repo / path / ".jvm/src/main/scala/izumi/distage/modules/platform") else Nil)
  },
  Compile / unmanagedSources / excludeFilter := (if (native05 && id == "platform") new SimpleFileFilter(f => f.getPath.contains("/.jvm/") && f.getName == "__AbstractIzPlatformPlatformSpecific.scala") else if (native05 && id == "bio") new SimpleFileFilter(f => f.getPath.contains("/.jvm/") && Set("__PlatformSpecific.scala", "IzUUIDPlatformSpecific.scala", "__SecureRandomPlatformSpecific.scala", "UnsafeRun2.scala", "QuasiIORunner.scala")(f.getName)) else if (native05 && id == "core") new SimpleFileFilter(f => (f.getPath.contains("/src/main/scala/izumi/distage/modules/typeclass/ZIOCatsEffectInstancesModule.scala") && !f.getPath.contains("native-standins")) || (f.getPath.contains("/.js/") && f.getName == "CatsIOPlatformDependentSupportModule.scala") || (f.getPath.contains("/.jvm/") && f.getName != "CatsIOPlatformDependentSupportModule.scala")) else if (sys.props.contains("spike.nativeAdapter") && id == "bio") new SimpleFileFilter(f => f.getPath.contains("/.jvm/") && f.getName == "__PlatformSpecific.scala") else if (sys.props.contains("spike.catsJvmAdapter") && id == "core") new SimpleFileFilter(f => (f.getPath.contains("/.js/") && f.getName == "CatsIOPlatformDependentSupportModule.scala") || (f.getPath.contains("/.jvm/") && f.getName != "CatsIOPlatformDependentSupportModule.scala")) else HiddenFileFilter),
  scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-Xsource:3", "-P:kind-projector:underscore-placeholders") else Seq("-Xkind-projector:underscores", "-Yretain-trees", "-no-indent") ++ (if (native05) Seq("-Xmax-inlines:64") else Nil)),
  publishMavenStyle := true,
  Compile / unmanagedSources ++= (if (native05 && id == "bio") Seq(repo / path / ".js/src/main/scala/izumi/fundamentals/platform/uuid/IzUUIDPlatformSpecific.scala") else Nil),
  Compile / sourceGenerators += Def.task {
    if (native05 && id == "bio") {
      val original = repo / path / ".jvm/src/main/scala/izumi/functional/bio/UnsafeRun2.scala"
      val securityManagerLookup = "Option(System.getSecurityManager).fold(Thread.currentThread().getThreadGroup)(_.getThreadGroup)"
      val text = IO.read(original)
      require(text.contains(securityManagerLookup), s"UnsafeRun2 SecurityManager lookup changed in $original")
      val out = (Compile / sourceManaged).value / "native" / "UnsafeRun2.scala"
      IO.write(out, text.replace(securityManagerLookup, "Thread.currentThread().getThreadGroup"))
      // java.util.UUID.randomUUID needs java.security.SecureRandom, which Scala Native's javalib lacks
      val runnerOriginal = repo / path / ".jvm/src/main/scala/izumi/functional/quasi/QuasiIORunner.scala"
      val randomUuid = "java.util.UUID.randomUUID()"
      val runnerText = IO.read(runnerOriginal)
      require(runnerText.contains(randomUuid), s"QuasiIORunner UUID usage changed in $runnerOriginal")
      val runnerOut = (Compile / sourceManaged).value / "native" / "QuasiIORunner.scala"
      IO.write(runnerOut, runnerText.replace(randomUuid, "izumi.fundamentals.platform.uuid.IzUUID.generateRandomUUID()"))
      Seq(out, runnerOut)
    } else Nil
  }.taskValue
)
'''
body = ['lazy val interopStandin = Project("interopStandin", file("modules/interopStandin")).enablePlugins(ScalaNativePlugin).settings(Compile / unmanagedSourceDirectories := Seq(file("native-standins/interop").getCanonicalFile))']
for name,(path,deps) in modules.items():
    line = f'lazy val {name} = module("{name}", "{path}")'
    if deps: line += '.dependsOn(' + ', '.join(deps) + ')'
    if name == 'orphans': line += '.dependsOn((if (native05) Seq[ClasspathDep[ProjectReference]](interopStandin % Provided) else Nil): _*)'
    body.append(line)
body.append('lazy val app = Project("app", file("app")).enablePlugins(ScalaNativePlugin).dependsOn(testkit).settings(libraryDependencies ++= nativeDeps.value, Compile / mainClass := Some("izumi.distage.PortabilityMain"))')
body.append('lazy val root = Project("root", file(".")).aggregate(' + ', '.join(modules) + ', app).settings(publish / skip := true)')
(here/'fixture/build.sbt').write_text(header + '\n'.join(body) + '\n')
(here/'closure.tsv').write_text('module\trepository path\tproject dependencies\n' + ''.join(f'{n}\t{p}\t{",".join(d)}\n' for n,(p,d) in modules.items()))
