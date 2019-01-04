package com.github.pshirshov.izumi.idealingua

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.il.loader._
import com.github.pshirshov.izumi.idealingua.il.renderer.{IDLRenderer, IDLRenderingOptions}
import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}
import com.github.pshirshov.izumi.idealingua.translator._
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension

import scala.sys.process._

@ExposedTestScope
final case class CompilerOutput(targetDir: Path, allFiles: Seq[Path]) {
  def absoluteTargetDir: Path = targetDir.toAbsolutePath

  def phase3: Path = absoluteTargetDir.getParent.resolve("phase3-compiler-output")

  def phase3Relative: Path = absoluteTargetDir.relativize(phase3)

  def relativeOutputs: Seq[String] = allFiles.map(p => absoluteTargetDir.relativize(p.toAbsolutePath).toString)
}


@ExposedTestScope
object IDLTestTools {
  def loadDefs(): Seq[LoadedDomain.Success] = loadDefs("/defs/any")

  def loadDefs(base: String): Seq[LoadedDomain.Success] = loadDefs(makeLoader(base), makeResolver(base))


  def makeLoader(base: String): LocalModelLoaderContext = {
    val src = new File(getClass.getResource(base).toURI).toPath
    val context = new LocalModelLoaderContext(src, Seq.empty)
    context
  }

  def makeResolver(base: String): ModelResolver = {
    val last = base.split("/").last
    val rules = if (last == "any") {
      TypespaceCompilerBaseFacade.descriptors.flatMap(_.rules)
    } else {
      TypespaceCompilerBaseFacade.descriptor(IDLLanguage.parse(last)).rules
    }
    new ModelResolver(rules)
  }


  def loadDefs(context: LocalModelLoaderContext, resolver: ModelResolver): Seq[LoadedDomain.Success] = {
    val loaded = context.loader.load()
    val resolved = resolver.resolve(loaded).ifWarnings(w => System.err.println(w)).throwIfFailed()

    val loadable = context.enumerator.enumerate().filter(_._1.name.endsWith(context.domainExt)).keySet
    val good = resolved.successful.map(_.path).toSet
    val failed = loadable.diff(good)
    assert(failed.isEmpty, s"domains were not loaded: $failed")

    resolved.successful
  }

  def compilesScala(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[ScalaTranslatorExtension] = ScalaTranslator.defaultExtensions): Boolean = {
    val manifest = ScalaBuildManifest.default
    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Scala, extensions, manifest))
    val classpath: String = IzJvm.safeClasspath()

    val cmd = Seq(
      "scalac"
      , "-deprecation"
      , "-opt-warnings:_"
      , "-d", out.phase3Relative.toString
      , "-classpath", classpath
    ) ++ out.relativeOutputs

    val exitCode = run(out.absoluteTargetDir, cmd, Map.empty, "scalac")
    exitCode == 0
  }

  def compilesTypeScript(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[TypeScriptTranslatorExtension] = TypeScriptTranslator.defaultExtensions, scoped: Boolean): Boolean = {
    val manifest = TypeScriptBuildManifest.default.copy(
      moduleSchema = if (scoped) TypeScriptModuleSchema.PER_DOMAIN else TypeScriptModuleSchema.UNITED
    )
    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Typescript, extensions, manifest))

    val outputTsconfigPath = out.targetDir.resolve("tsconfig.json")
    val tsconfigBytes = new String(Files.readAllBytes(outputTsconfigPath), StandardCharsets.UTF_8)
      .replace("\"dist\"", s""""${out.phase3.toString}"""")
      .getBytes
    Files.write(outputTsconfigPath, tsconfigBytes)

    if (run(out.absoluteTargetDir, Seq("yarn", "install"), Map.empty, "yarn") != 0) {
      return false
    }

    val tscCmd = Seq("tsc", "-p", "tsconfig.json")

    val exitCode = run(out.absoluteTargetDir, tscCmd, Map.empty, "tsc")
    exitCode == 0
  }

  def compilesCSharp(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[CSharpTranslatorExtension] = CSharpTranslator.defaultExtensions): Boolean = {
    val manifest = CSharpBuildManifest.default
    val lang = IDLLanguage.CSharp
    val out = compiles(id, domains, CompilerOptions(lang, extensions, manifest))
    val refsDir = out.absoluteTargetDir.resolve("refs")

    IzFiles.recreateDirs(refsDir)

    val refsSrc = s"refs/${lang.toString.toLowerCase()}"
    val refDlls = IzResources.copyFromClasspath(refsSrc, refsDir).files
      .filter(f => f.toFile.isFile && f.toString.endsWith(".dll")).map(f => out.absoluteTargetDir.relativize(f.toAbsolutePath))
    IzResources.copyFromClasspath(refsSrc, out.phase3)


    val outname = "test-output.dll"
    val refs = s"/reference:${refDlls.mkString(",")}"
    val cmdBuild = Seq("csc", "-target:library", s"-out:${out.phase3Relative}/$outname", "-recurse:\\*.cs", refs)
    val exitCodeBuild = run(out.absoluteTargetDir, cmdBuild, Map.empty, "cs-build")

    val cmdTest = Seq("nunit-console", outname)
    val exitCodeTest = run(out.phase3, cmdTest, Map.empty, "cs-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  def compilesGolang(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[GoLangTranslatorExtension] = GoLangTranslator.defaultExtensions, repoLayout: Boolean): Boolean = {
    val manifest = GoLangBuildManifest.default.copy(
      useRepositoryFolders = repoLayout
    )
    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Go, extensions, manifest))
    val outDir = out.absoluteTargetDir

    val tmp = outDir.getParent.resolve("phase2-compiler-tmp")
    tmp.toFile.mkdirs()
    Files.move(outDir, tmp.resolve("src"))
    Files.move(tmp, outDir)

    val env = Map("GOPATH" -> out.absoluteTargetDir.toString)
    val goSrc = out.absoluteTargetDir.resolve("src")
    if (manifest.dependencies.nonEmpty) {
      manifest.dependencies.foreach(md => {
        run(goSrc, Seq("go", "get", md.module), env, "go-dep-install")
      })
    }

    val cmdBuild = Seq("go", "install", "-pkgdir", out.phase3.toString, "./...")
    val cmdTest = Seq("go", "test", "./...")


    val exitCodeBuild = run(goSrc, cmdBuild, env, "go-build")
    val exitCodeTest = run(goSrc, cmdTest, env, "go-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  private def compiles[E <: TranslatorExtension, M <: BuildManifest](id: String, domains: Seq[LoadedDomain.Success], options: CompilerOptions[E, M]): CompilerOutput = {
    val targetDir = Paths.get("target")
    val tmpdir = targetDir.resolve("idl-output")

    Quirks.discard(tmpdir.toFile.mkdirs())

    // TODO: clashes still may happen in case of parallel runs with the same ID
    val stablePrefix = s"$id-${options.language.toString}"
    val vmPrefix = s"$stablePrefix-u${ManagementFactory.getRuntimeMXBean.getStartTime}"
    val dirPrefix = s"$vmPrefix-ts${System.currentTimeMillis()}"

    dropOldRunsData(tmpdir, stablePrefix, vmPrefix)

    val runDir = tmpdir.resolve(dirPrefix)
    val domainsDir = runDir.resolve("phase0-rerender")
//    val layoutDir = runDir.resolve("phase1-layout")
    val compilerDir = runDir.resolve("phase2-compiler-input")

    IzFiles.recreateDirs(runDir, domainsDir, compilerDir)
    IzFiles.refreshSymlink(targetDir.resolve(stablePrefix), runDir)

    //val options = TypespaceCompiler.UntypedCompilerOptions(language, extensions)

    val products = new TypespaceCompilerFSFacade(domains)
      .compile(compilerDir, UntypedCompilerOptions(options.language, options.extensions, options.manifest, options.withBundledRuntime))
      .compilationProducts
    assert(products.paths.toSet.size == products.paths.size)

    rerenderDomains(domainsDir, domains)
//    saveDebugLayout(layoutDir, products)

    val out = CompilerOutput(compilerDir, products.paths)
    out.phase3.toFile.mkdirs()
    out
  }


  private def rerenderDomains(domainsDir: Path, domains: Seq[LoadedDomain.Success]): Unit = {
    domains.foreach {
      d =>
        val rendered = new IDLRenderer(d.typespace.domain, IDLRenderingOptions(expandIncludes = false)).render()
        Files.write(domainsDir.resolve(s"${d.typespace.domain.id.id}.domain"), rendered.getBytes(StandardCharsets.UTF_8))
    }
  }

//  private def saveDebugLayout(layoutDir: Path, products: IDLCompilationResult): Unit = {
//    val mapped = products.paths.map {
//      f =>
//        val domainDir = layoutDir.resolve(products.id.toPackage.mkString("."))
//        val marker = products.id.toPackage.mkString("/")
//        val target = if (f.toString.contains(marker)) {
//          domainDir.resolve(f.toFile.getName)
//        } else {
//
//          domainDir.resolve(products.target.relativize(f))
//        }
//        (f, target)
//    }
//    mapped.foreach {
//      case (src, tgt) =>
//        tgt.getParent.toFile.mkdirs()
//        Files.copy(src, tgt)
//    }
//
//    assert(products.paths.toSet.size == products.paths.size)
//  }

  private def dropOldRunsData(tmpdir: Path, stablePrefix: String, vmPrefix: String): Unit = {
    tmpdir
      .toFile
      .listFiles()
      .toList
      .filter(f => f.isDirectory && f.getName.startsWith(stablePrefix) && !f.getName.startsWith(vmPrefix))
      .foreach {
        f =>
          Quirks.discard(IzFiles.removeDir(f.toPath))
      }
  }

  private def run(workDir: Path, cmd: Seq[String], env: Map[String, String], cname: String): Int = {
    val cmdlog = workDir.getParent.resolve(s"$cname.sh")
    val commands = Seq(s"cd ${workDir.toAbsolutePath}") ++ env.map(kv => s"export ${kv._1}=${kv._2}") ++ Seq(cmd.mkString(" "))
    val cmdSh = commands.mkString("\n")
    Files.write(cmdlog, cmdSh.getBytes)

    val log = workDir.getParent.resolve(s"$cname.log").toFile
    val logger = ProcessLogger(log)
    val exitCode = try {
      Process(cmd, Some(workDir.toFile), env.toSeq: _*)
        .run(logger)
        .exitValue()
    } finally {
      logger.close()
    }

    if (exitCode != 0) {
      System.err.println(s"Process failed for $cname: $exitCode")
      println(cmdSh)
      System.err.println(IzFiles.readString(log))
    }
    exitCode
  }
}
