package com.github.pshirshov.izumi.idealingua

import java.io.File

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.loader.{FSPath, LoadedModels}
import com.github.pshirshov.izumi.idealingua.typer2.TyperOptions
//import java.lang.management.ManagementFactory
//import java.nio.charset.StandardCharsets
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.idealingua.il.loader._
//import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
//import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
//import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
//import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
//import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
//import com.github.pshirshov.izumi.idealingua.model.publishing.manifests._
//import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
//import com.github.pshirshov.izumi.idealingua.translator.tocsharp.layout.CSharpNamingConvention
//import scala.sys.process._

import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain


@ExposedTestScope
final case class CompilerOutput(targetDir: Path, allFiles: Seq[Path]) {
  def absoluteTargetDir: Path = targetDir.toAbsolutePath

  def phase2: Path = absoluteTargetDir.getParent.resolve("phase2-compiler-output")

  def phase2Relative: Path = absoluteTargetDir.relativize(phase2)

  def relativeOutputs: Seq[String] = allFiles.map(p => absoluteTargetDir.relativize(p.toAbsolutePath).toString)
}


@ExposedTestScope
object IDLTestTools {
  def loadDefs(): Seq[LoadedDomain.Success] = loadDefs("/defs/any")

  def loadDefs(base: String): Seq[LoadedDomain.Success] = loadDefs(makeLoader(base), makeResolver(base))


  def makeLoader(base: String): LocalModelLoaderContext = {
    val src = new File(getClass.getResource(base).toURI).toPath
    val context = new LocalModelLoaderContext(Seq(src), Seq.empty)
    context
  }

  def makeResolver(base: String): ModelResolver = {
//    val last = base.split('/').last
//    val rules = if (last == "any") {
//      TypespaceCompilerBaseFacade.descriptors.flatMap(_.rules)
//    } else {
//      TypespaceCompilerBaseFacade.descriptor(IDLLanguage.parse(last)).rules
//    }
//    new ModelResolver(rules)
    base.discard()
    new ModelResolver(TyperOptions())
  }

  sealed trait Expectation
  object Expectation {
    final case class Failure(withWarnings: Boolean) extends Expectation
    final case class Success(withWarnings: Boolean) extends Expectation
  }

  sealed trait FailedExpectation
  object FailedExpectation {
    final case class FailureExpected(path: FSPath) extends FailedExpectation
    final case class SuccessExpected(path: FSPath) extends FailedExpectation
    final case class WarningsExpected(path: FSPath) extends FailedExpectation
    final case class UnexpectedWarnings(path: FSPath) extends FailedExpectation
    final case class UnexpectedFailure(f: LoadedDomain.Failure) extends FailedExpectation
  }


  def loadDefs(context: LocalModelLoaderContext, resolver: ModelResolver): Seq[LoadedDomain.Success] = {
    val loaded = context.loader.load()
    val resolved = resolver.resolve(loaded) //.ifWarnings(w => System.err.println(w)).throwIfFailed()
    verifyAllDomainsWereProcessed(context, resolved)

    val expectations: Map[FSPath, Expectation] = Map(
      FSPath.parse("/idltest/streams.domain") -> Expectation.Failure(false),
      FSPath.parse("/idltest/withoverlay.domain") -> Expectation.Failure(false),
      FSPath.parse("/idltest/clones.domain") -> Expectation.Success(true),
      FSPath.parse("/idltest/substraction.domain") -> Expectation.Success(true),
      FSPath.parse("/idltest/syntax.domain") -> Expectation.Success(true),
      FSPath.parse("/idltest/templates.domain") -> Expectation.Success(true),
      FSPath.parse("/overlaytest/withoverlay.domain") -> Expectation.Failure(false),
    )

    val failedExpectations = resolved.all.map {
      case LoadedDomain.Success(typespace) =>
        expectations.get(typespace.origin) match {
          case Some(value) =>
            value match {
              case Expectation.Failure(_) =>
                Left(FailedExpectation.FailureExpected(typespace.origin))
              case Expectation.Success(withWarnings) =>
                if (typespace.warnings.isEmpty == !withWarnings) {
                  Right(())
                } else {
                  Left(FailedExpectation.WarningsExpected(typespace.origin))
                }
            }
          case None =>
            if (typespace.warnings.nonEmpty) {
              Left(FailedExpectation.UnexpectedWarnings(typespace.origin))
            } else {
              Right(())
            }
        }

      case failure: LoadedDomain.Failure =>
        failure match {
          case f: LoadedDomain.ParsingFailed =>
            Left(FailedExpectation.UnexpectedFailure(f))

          case f: LoadedDomain.ResolutionFailed =>
            Left(FailedExpectation.UnexpectedFailure(f))

          case LoadedDomain.TyperFailed(path, _, _, warnings) =>

            expectations.get(path) match {
              case Some(value) =>
                value match {
                  case Expectation.Failure(withWarnings) =>
                    if (warnings.isEmpty == !withWarnings) {
                      Right(())
                    } else {
                      Left(FailedExpectation.WarningsExpected(path))
                    }

                  case Expectation.Success(_) =>
                    Left(FailedExpectation.FailureExpected(path))

                }
              case None =>
                Left(FailedExpectation.SuccessExpected(path))
            }
        }
    }.collect({case Left(f) => f})

    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    assert(failedExpectations.isEmpty, s"failed test expectations: ${failedExpectations.niceList()}")
    //    val good = resolved.successful.map(_.typespace.origin).toSet


    resolved.successful
  }

//  def compilesScala(id: String, domains: Seq[LoadedDomain.Success], layout: ScalaProjectLayout, extensions: Seq[ScalaTranslatorExtension] = ScalaTranslator.defaultExtensions): Boolean = {
//    val mf = ScalaBuildManifest.example
//    val manifest = mf.copy(layout = ScalaProjectLayout.SBT, sbt = mf.sbt.copy(projectNaming = mf.sbt.projectNaming.copy(dropFQNSegments = Some(1))))
//    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Scala, extensions, manifest))
//    val classpath: String = IzJvm.safeClasspath()
//
//
//    val cmd = layout match {
//      case ScalaProjectLayout.PLAIN =>
//        Seq(
//          "scalac"
//          , "-deprecation"
//          , "-opt-warnings:_"
//          , "-d", out.phase2Relative.toString
//          , "-classpath", classpath
//        ) ++ out.relativeOutputs.filter(_.endsWith(".scala"))
//      case ScalaProjectLayout.SBT =>
//        Seq("sbt", "clean", "compile")
//    }
//
//    val exitCode = run(out.absoluteTargetDir, cmd, Map.empty, "scalac")
//    exitCode == 0
//  }
//
//  def compilesTypeScript(id: String, domains: Seq[LoadedDomain.Success], layout: TypeScriptProjectLayout, extensions: Seq[TypeScriptTranslatorExtension] = TypeScriptTranslator.defaultExtensions): Boolean = {
//    val manifest = TypeScriptBuildManifest.example.copy(layout = layout)
//    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Typescript, extensions, manifest))
//
//    val outputTsconfigPath = out.targetDir.resolve("tsconfig.json")
//    val tsconfigBytes = new String(Files.readAllBytes(outputTsconfigPath), StandardCharsets.UTF_8)
//      .replace("\"dist\"", s""""${out.phase2.toString}"""")
//      .getBytes
//    Files.write(outputTsconfigPath, tsconfigBytes)
//
//    if (run(out.absoluteTargetDir, Seq("yarn", "install"), Map.empty, "yarn") != 0) {
//      return false
//    }
//
//
//    val tscCmd = layout match {
//      case TypeScriptProjectLayout.YARN =>
//        Seq("yarn", "build")
//      case TypeScriptProjectLayout.PLAIN =>
//        Seq("tsc", "-p", "tsconfig.json")
//    }
//
//    val exitCode = run(out.absoluteTargetDir, tscCmd, Map.empty, "tsc")
//    exitCode == 0
//  }
//
//  def compilesCSharp(id: String, domains: Seq[LoadedDomain.Success], layout: CSharpProjectLayout, extensions: Seq[CSharpTranslatorExtension] = CSharpTranslator.defaultExtensions): Boolean = {
//    val mf = CSharpBuildManifest.example
//    val manifest = mf.copy(layout = layout)
//
//    val lang = IDLLanguage.CSharp
//    val out = compiles(id, domains, CompilerOptions(lang, extensions, manifest))
//
//    layout match {
//      case CSharpProjectLayout.NUGET =>
//        val conv = new CSharpNamingConvention(manifest.nuget.projectNaming)
//        val cmdNuget = Seq("nuget", "pack", s"nuspec/${conv.nuspecName(conv.pkgId)}")
//        val exitCodeBuild = run(out.targetDir, cmdNuget, Map.empty, "cs-nuget")
//        val cmdMsbuild = Seq("msbuild", "/t:Restore", "/t:Rebuild")
//        val exitCodeMsBuild = run(out.targetDir, cmdMsbuild, Map.empty, "cs-msbuild")
//        exitCodeBuild == 0 && exitCodeMsBuild == 0
//
//      case CSharpProjectLayout.PLAIN =>
//        val refsDir = out.absoluteTargetDir.resolve("refs")
//
//        IzFiles.recreateDirs(refsDir)
//
//        val refsSrc = s"refs/${lang.toString.toLowerCase()}"
//        val refDlls = IzResources.copyFromClasspath(refsSrc, refsDir).files
//          .filter(f => f.toFile.isFile && f.toString.endsWith(".dll")).map(f => out.absoluteTargetDir.relativize(f.toAbsolutePath))
//        IzResources.copyFromClasspath(refsSrc, out.phase2)
//
//
//        val outname = "test-output.dll"
//        val refs = s"/reference:${refDlls.mkString(",")}"
//        val cmdBuild = Seq("csc", "-target:library", s"-out:${out.phase2Relative}/$outname", "-recurse:\\*.cs", refs)
//        val exitCodeBuild = run(out.absoluteTargetDir, cmdBuild, Map.empty, "cs-build")
//
//        val cmdTest = Seq("nunit-console", outname)
//        val exitCodeTest = run(out.phase2, cmdTest, Map.empty, "cs-test")
//
//        exitCodeBuild == 0 && exitCodeTest == 0
//
//    }
//  }
//
//  def compilesGolang(id: String, domains: Seq[LoadedDomain.Success], layout: GoProjectLayout, extensions: Seq[GoLangTranslatorExtension] = GoLangTranslator.defaultExtensions): Boolean = {
//    val mf = GoLangBuildManifest.example
//    val manifest = mf.copy(layout = layout)
//    val out = compiles(id, domains, CompilerOptions(IDLLanguage.Go, extensions, manifest))
//    val outDir = out.absoluteTargetDir
//
//    val tmp = outDir.getParent.resolve("phase1-compiler-tmp")
//    tmp.toFile.mkdirs()
//    Files.move(outDir, tmp.resolve("src"))
//    Files.move(tmp, outDir)
//
//    val env = Map("GOPATH" -> out.absoluteTargetDir.toString)
//    val goSrc = out.absoluteTargetDir.resolve("src")
//    if (manifest.repository.dependencies.nonEmpty) {
//      manifest.repository.dependencies.foreach(md => {
//        run(goSrc, Seq("go", "get", md.module), env, "go-dep-install")
//      })
//    }
//
//    val cmdBuild = Seq("go", "install", "-pkgdir", out.phase2.toString, "./...")
//    val cmdTest = Seq("go", "test", "./...")
//
//
//    val exitCodeBuild = run(goSrc, cmdBuild, env, "go-build")
//    val exitCodeTest = run(goSrc, cmdTest, env, "go-test")
//
//    exitCodeBuild == 0 && exitCodeTest == 0
//  }
//
//  private def compiles[E <: TranslatorExtension, M <: BuildManifest](id: String, domains: Seq[LoadedDomain.Success], options: CompilerOptions[E, M]): CompilerOutput = {
//    val targetDir = Paths.get("target")
//    val tmpdir = targetDir.resolve("idl-output")
//
//    Quirks.discard(tmpdir.toFile.mkdirs())
//
//    // TODO: clashes still may happen in case of parallel runs with the same ID
//    val stablePrefix = s"$id-${options.language.toString}"
//    val vmPrefix = s"$stablePrefix-u${ManagementFactory.getRuntimeMXBean.getStartTime}"
//    val dirPrefix = s"$vmPrefix-ts${System.currentTimeMillis()}"
//
//    dropOldRunsData(tmpdir, stablePrefix, vmPrefix)
//
//    val runDir = tmpdir.resolve(dirPrefix)
//    val domainsDir = runDir.resolve("phase0-rerender")
//    val compilerDir = runDir.resolve("phase1-compiler-input")
//
//    IzFiles.recreateDirs(runDir, domainsDir, compilerDir)
//    IzFiles.refreshSymlink(targetDir.resolve(stablePrefix), runDir)
//
//
//    val products = new TypespaceCompilerFSFacade(domains)
//      .compile(compilerDir, UntypedCompilerOptions(options.language, options.extensions, options.manifest, options.withBundledRuntime))
//      .compilationProducts
//    assert(products.paths.toSet.size == products.paths.size)
//
//    rerenderDomains(domainsDir, domains)
//
//    val out = CompilerOutput(compilerDir, products.paths)
//    out.phase2.toFile.mkdirs()
//    out
//  }
//
//
//  private def rerenderDomains(domainsDir: Path, domains: Seq[LoadedDomain.Success]): Unit = {
//    domains.foreach {
//      d =>
//        val rendered = new IDLRenderer(d.typespace.domain, IDLRenderingOptions(expandIncludes = false)).render()
//        Files.write(domainsDir.resolve(s"${d.typespace.domain.id.id}.domain"), rendered.utf8)
//    }
//  }
//
//
//  private def dropOldRunsData(tmpdir: Path, stablePrefix: String, vmPrefix: String): Unit = {
//    tmpdir
//      .toFile
//      .listFiles()
//      .toList
//      .filter(f => f.isDirectory && f.getName.startsWith(stablePrefix) && !f.getName.startsWith(vmPrefix))
//      .foreach {
//        f =>
//          Quirks.discard(IzFiles.removeDir(f.toPath))
//      }
//  }
//
//  private def run(workDir: Path, cmd: Seq[String], env: Map[String, String], cname: String): Int = {
//    val cmdlog = workDir.getParent.resolve(s"$cname.sh")
//    val commands = Seq(s"cd ${workDir.toAbsolutePath}") ++ env.map(kv => s"export ${kv._1}=${kv._2}") ++ Seq(cmd.mkString(" "))
//    val cmdSh = commands.mkString("\n")
//    Files.write(cmdlog, cmdSh.getBytes)
//
//    val log = workDir.getParent.resolve(s"$cname.log").toFile
//    val logger = ProcessLogger(log)
//    val exitCode = try {
//      Process(cmd, Some(workDir.toFile), env.toSeq: _*)
//        .run(logger)
//        .exitValue()
//    } finally {
//      logger.close()
//    }
//
//    if (exitCode != 0) {
//      System.err.println(s"Process failed for $cname: $exitCode")
//      println(cmdSh)
//      System.err.println(IzFiles.readString(log))
//    }
//    exitCode
//  }
  private def verifyAllDomainsWereProcessed(context: LocalModelLoaderContext, resolved: LoadedModels) = {
    val all = resolved.all.map {
      case LoadedDomain.Success(typespace) =>
        typespace.origin
      case failure: LoadedDomain.Failure =>
        failure.path
    }.toSet
    val loadable = context.enumerator.enumerate().filter(_._1.name.endsWith(context.domainExt)).keySet
    val failed = loadable.diff(all)
    val unknown = all.diff(loadable)
    assert(failed.isEmpty, s"domains were not processed: $failed")
    assert(unknown.isEmpty, s"unknown domains: $unknown")
  }
}
