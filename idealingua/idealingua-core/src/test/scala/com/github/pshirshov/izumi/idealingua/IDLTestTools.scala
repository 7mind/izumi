package com.github.pshirshov.izumi.idealingua

import java.io.File
import java.lang.management.ManagementFactory
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.il.renderer.ILRenderer
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage, TranslatorExtension, TypespaceCompiler}

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
  def makeLoader(): LocalModelLoader = {
    val src = new File(getClass.getResource("/defs").toURI).toPath
    new LocalModelLoader(src, Seq.empty)
  }

  def loadDefs(): Seq[Typespace] = loadDefs(makeLoader())

  def loadDefs(loader: LocalModelLoader): Seq[Typespace] = {
    val loaded = loader.load()

    val loadableCount = loader.enumerate().count(_._1.toString.endsWith(LocalModelLoader.domainExt))
    assert(loaded.size == loadableCount, s"expected $loadableCount domains")

    loaded
  }

  def compilesScala(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = ScalaTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Scala, extensions)
    val classLoader = Thread
      .currentThread
      .getContextClassLoader
      .getParent


    val classpath = classLoader match {
      case u: URLClassLoader =>
        u
          .getURLs
          .map(_.getFile)
          .mkString(System.getProperty("path.separator"))
      case _ =>
        System.getProperty("java.class.path")
    }

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

  def compilesTypeScript(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Typescript, extensions)

    val outputTsconfigPath = out.targetDir.resolve("tsconfig.json")
    val tsconfigBytes = IzResources.readAsString("tsconfig-compiler-test.json")
      .get
      .replace("../phase3-compiler-output", out.phase3.toString)

    Files.write(outputTsconfigPath, tsconfigBytes.getBytes)

    val cmd = Seq("tsc", "-p", outputTsconfigPath.toFile.getName)

    val exitCode = run(out.absoluteTargetDir, cmd, Map.empty, "tsc")
    exitCode == 0
  }

  def compilesCSharp(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = CSharpTranslator.defaultExtensions): Boolean = {
    val refDlls = Seq(
      "Newtonsoft.Json.dll",
      "UnityEngine.dll",
      "UnityEngine.Networking.dll",
      "2.4.8-net2.0-nunit.framework.dll"
    )

    val lang = IDLLanguage.CSharp
    val out = compiles(id, domains, lang, extensions, refDlls)
    val refsDir = out.absoluteTargetDir.resolve("refs")

    IzFiles.recreateDirs(refsDir)

    val refsSrc = s"refs/${lang.toString.toLowerCase()}"
    IzResources.copyFromClasspath(refsSrc, refsDir)
    IzResources.copyFromClasspath(refsSrc, out.phase3)


    val outname = "test-output.dll"
    val refs = s"/reference:${refDlls.map(dll => s"refs/$dll").mkString(",")}"
    val cmdBuild = Seq("csc", "-target:library", s"-out:${out.phase3Relative}/$outname", "-recurse:\\*.cs", refs)
    val exitCodeBuild = run(out.absoluteTargetDir, cmdBuild, Map.empty, "cs-build")

    val cmdTest = Seq("nunit-console", outname)
    val exitCodeTest = run(out.phase3, cmdTest, Map.empty, "cs-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  def compilesGolang(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Go, extensions)
    val outDir = out.absoluteTargetDir

    val tmp = outDir.getParent.resolve("phase2-compiler-tmp")
    tmp.toFile.mkdirs()
    Files.move(outDir, tmp.resolve("src"))
    Files.move(tmp, outDir)

    val cmdBuild = Seq("go", "install", "-pkgdir", out.phase3.toString, "./...")
    val cmdTest = Seq("go", "test", "./...")

    val env = Map("GOPATH" -> out.absoluteTargetDir.toString)
    val goSrc = out.absoluteTargetDir.resolve("src")
    val exitCodeBuild = run(goSrc, cmdBuild, env, "go-build")
    val exitCodeTest = run(goSrc, cmdTest, env, "go-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  private def compiles(id: String, domains: Seq[Typespace], language: IDLLanguage, extensions: Seq[TranslatorExtension], refFiles: Seq[String] = Seq.empty): CompilerOutput = {
    val targetDir = Paths.get("target")
    val tmpdir = targetDir.resolve("idl-output")

    Quirks.discard(tmpdir.toFile.mkdirs())

    // TODO: clashes still may happen in case of parallel runs with the same ID
    val stablePrefix = s"$id-${language.toString}"
    val vmPrefix = s"$stablePrefix-u${ManagementFactory.getRuntimeMXBean.getStartTime}"
    val dirPrefix = s"$vmPrefix-ts${System.currentTimeMillis()}"

    dropOldRunsData(tmpdir, stablePrefix, vmPrefix)

    val runDir = tmpdir.resolve(dirPrefix)
    val domainsDir = runDir.resolve("phase0-rerender")
    val layoutDir = runDir.resolve("phase1-layout")
    val compilerDir = runDir.resolve("phase2-compiler-input")

    IzFiles.recreateDirs(runDir, domainsDir, layoutDir, compilerDir)
    IzFiles.refreshSymlink(targetDir.resolve(stablePrefix), runDir)

    val options = TypespaceCompiler.CompilerOptions(language, extensions)

    val allFiles: Seq[Path] = new IDLCompiler(domains)
      .compile(compilerDir, options)
      .invokation.flatMap {
      case (did, s) =>
        val mapped = s.paths.map {
          f =>
            val domainDir = layoutDir.resolve(did.toPackage.mkString("."))
            (f, domainDir.resolve(f.toFile.getName))
        }

        mapped.foreach {
          case (src, tgt) =>
            tgt.getParent.toFile.mkdirs()
            Files.copy(src, tgt)
        }

        assert(s.paths.toSet.size == s.paths.size)

        s.paths
    }.toSeq

    domains.foreach {
      d =>
        val rendered = new ILRenderer(d.domain).render()
        Files.write(domainsDir.resolve(s"${d.domain.id.id}.domain"), rendered.getBytes(StandardCharsets.UTF_8))
    }

    val out = CompilerOutput(compilerDir, allFiles)
    out.phase3.toFile.mkdirs()
    out
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
    val commands = Seq(s"cd ${workDir.toAbsolutePath}") ++ env.map(kv => s"export ${kv._1}=${kv._2}") ++ Seq(cmd.mkString(" "))
    println(commands.mkString("\n"))

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
      System.err.println(IzFiles.readString(log))
    }
    exitCode
  }
}
