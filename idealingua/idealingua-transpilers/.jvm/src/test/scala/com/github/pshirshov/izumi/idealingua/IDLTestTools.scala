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
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{GoLangBuildManifest, TypeScriptBuildManifest, TypeScriptModuleSchema}
import com.github.pshirshov.izumi.idealingua.model.publishing.{BuildManifest, ManifestDependency, Publisher}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator._

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
  def makeLoader(): LocalModelLoaderContext = {
    val src = new File(getClass.getResource("/defs").toURI).toPath
    val context = new LocalModelLoaderContext(src, Seq.empty)
    context
  }

  def makeResolver(): ModelResolver = {
    val rules = TypespaceCompilerBaseFacade.descriptors.flatMap(_.rules)
    new ModelResolver(rules)
  }

  def loadDefs(): Seq[LoadedDomain.Success] = loadDefs(makeLoader(), makeResolver())

  def loadDefs(context: LocalModelLoaderContext, resolver: ModelResolver): Seq[LoadedDomain.Success] = {
    val loaded = context.loader.load()
    val resolved = resolver.resolve(loaded).throwIfFailed()

    val loadable = context.enumerator.enumerate().filter(_._1.name.endsWith(context.domainExt)).keySet
    val good = resolved.successful.map(_.path).toSet
    val failed = loadable.diff(good)
    assert(failed.isEmpty, s"domains were not loaded: $failed")

    resolved.successful
  }

  def compilesScala(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[ScalaTranslatorExtension] = ScalaTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Scala, extensions))
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
    val manifest = new TypeScriptBuildManifest(
      name = "TestBuild",
      tags = "",
      description = "Test Description",
      notes = "",
      publisher = Publisher("Test Publisher Name", "test_publisher_id"),
      version = "0.0.0",
      license = "MIT",
      website = "http://project.website",
      copyright = "Copyright (C) Test Inc.",
      dependencies = List(ManifestDependency("moment", "^2.20.1"),
        ManifestDependency("@types/node", "^10.7.1"),
        //        ManifestDependency("websocket", "1.0.26"),
        ManifestDependency("@types/websocket", "0.0.39")
      ),
      scope = "@TestScope",
      moduleSchema = if (scoped) TypeScriptModuleSchema.PER_DOMAIN else TypeScriptModuleSchema.UNITED,
      None
    )

    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Typescript, extensions, true, if (scoped) Some(manifest) else None))

    val outputTspackagePath = out.targetDir.resolve("package.json")
    Files.write(outputTspackagePath, TypeScriptBuildManifest.generatePackage(manifest, "index", List("TestPackage"), List.empty).getBytes)
    val npmCmd = Seq("npm", "install")
    if (run(out.absoluteTargetDir, npmCmd, Map.empty, "npm") != 0) {
      return false
    }

    val outputTsconfigPath = out.targetDir.resolve("tsconfig.json")
    val tsconfigBytes = IzResources.readAsString("tsconfig-compiler-test.json")
      .get
      .replace("../phase3-compiler-output", out.phase3.toString)
    Files.write(outputTsconfigPath, tsconfigBytes.getBytes)
    val tscCmd = Seq("tsc", "-p", outputTsconfigPath.toFile.getName)

    val exitCode = run(out.absoluteTargetDir, tscCmd, Map.empty, "tsc")
    exitCode == 0
  }

  def compilesCSharp(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[CSharpTranslatorExtension] = CSharpTranslator.defaultExtensions): Boolean = {
    val lang = IDLLanguage.CSharp
    val out = compiles(id, domains, CompilerOptions(lang, extensions))
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

  def compilesGolang(id: String, domains: Seq[LoadedDomain.Success], extensions: Seq[GoLangTranslatorExtension] = GoLangTranslator.defaultExtensions, scoped: Boolean): Boolean = {
    val manifest = new GoLangBuildManifest(
      name = "TestBuild",
      tags = "",
      description = "Test Description",
      notes = "",
      publisher = Publisher("Test Publisher Name", "test_publisher_id"),
      version = "0.0.0",
      license = "MIT",
      website = "http://project.website",
      copyright = "Copyright (C) Test Inc.",
      dependencies = List(ManifestDependency("github.com/gorilla/websocket", "")),
      repository = "github.com/TestCompany/TestRepo",
      useRepositoryFolders = true
    )

    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Go, extensions, true, if (scoped) Some(manifest) else None))
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
    val layoutDir = runDir.resolve("phase1-layout")
    val compilerDir = runDir.resolve("phase2-compiler-input")

    IzFiles.recreateDirs(runDir, domainsDir, layoutDir, compilerDir)
    IzFiles.refreshSymlink(targetDir.resolve(stablePrefix), runDir)

    //val options = TypespaceCompiler.UntypedCompilerOptions(language, extensions)

    val products = new TypespaceCompilerFSFacade(domains)
      .compile(compilerDir, UntypedCompilerOptions(options.language, options.extensions, options.withRuntime, options.manifest))
      .compilationProducts

    val allPaths = products.flatMap(_._2.paths).toSeq

    rerenderDomains(domainsDir, domains)
    saveDebugLayout(layoutDir, products)

    val out = CompilerOutput(compilerDir, allPaths)
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

  private def saveDebugLayout(layoutDir: Path, products: Map[DomainId, IDLCompilationResult.Success]): Unit = {
    products.foreach {
      case (did, s) =>
        val mapped = s.paths.map {
          f =>
            val domainDir = layoutDir.resolve(did.toPackage.mkString("."))
            val marker = did.toPackage.mkString("/")
            val target = if (f.toString.contains(marker)) {
              domainDir.resolve(f.toFile.getName)
            } else {

              domainDir.resolve(s.target.relativize(f))
            }
            (f, target)
        }
        mapped.foreach {
          case (src, tgt) =>
            tgt.getParent.toFile.mkdirs()
            Files.copy(src, tgt)
        }

        assert(s.paths.toSet.size == s.paths.size)
    }
  }

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
