package com.github.pshirshov.izumi.idealingua

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file._
import sys.process._

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

    val ctarget = out.targetDir.getParent.resolve("phase3-scalac")
    ctarget.toFile.mkdirs()

    import scala.tools.nsc.{Global, Settings}
    val settings = new Settings()
    settings.d.value = ctarget.toString
    settings.feature.value = true
    settings.warnUnused.add("_")
    settings.deprecation.value = true
    settings.embeddedDefaults(this.getClass.getClassLoader)
    if (!isRunningUnderSbt) {
      settings.usejavacp.value = true
    }

    val g = new Global(settings)
    val run = new g.Run
    run.compile(out.allFiles.map(_.toFile.getCanonicalPath).toList)
    run.runIsAt(run.jvmPhase.next)
  }

  def compilesTypeScript(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Typescript, extensions)

    val outputTsconfigPath = out.targetDir.resolve("tsconfig.json")
    val tsconfigBytes = IzResources.readAsString("tsconfig-compiler-test.json")
    Files.write(outputTsconfigPath, tsconfigBytes.get.getBytes)

    val cmd = s"tsc -p ${outputTsconfigPath.toFile.getName}"
    val exitCode = run(out, cmd, Map.empty, "tsc")
    exitCode == 0
  }

  def compilesGolang(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Go, extensions)

    val tmp = out.targetDir.getParent.resolve("phase2-compiler-tmp")
    tmp.toFile.mkdirs()
    Files.move(out.targetDir, tmp.resolve("src"))
    Files.move(tmp, out.targetDir)

    val args = domains.map(d => d.domain.id.toPackage.mkString("/")).mkString(" ")
    //val cmdBuild= s"go build -o ../phase3-go $args" // go build: cannot use -o with multiple packages
    val cmdBuild = s"go build $args"
    val cmdTest = s"go test $args"

    val fullTarget = out.targetDir.toFile.getCanonicalPath

    println(
      s"""
         |cd $fullTarget
         |export GOPATH=$fullTarget
         |$cmdBuild
       """.stripMargin)

    val exitCodeBuild = run(out, cmdBuild, Map("GOPATH" -> fullTarget), "go-build")
    val exitCodeTest = run(out, cmdTest, Map("GOPATH" -> fullTarget), "go-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  private def run(out: CompilerOutput, cmd: String, env: Map[String, String], cname: String) = {
    val log = out.targetDir.getParent.resolve(s"$cname.log").toFile
    val logger = ProcessLogger(log)
    val exitCode = try {
      Process(cmd, Some(out.targetDir.toFile), env.toSeq: _*)
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

  def compilesCSharp(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = CSharpTranslator.defaultExtensions): Boolean = {
    val refDlls = Seq[String] (
      "Newtonsoft.Json.dll",
      "UnityEngine.dll",
      "UnityEngine.Networking.dll",
      "2.4.8-net2.0-nunit.framework.dll"
    )
    val out = compiles(id, domains, IDLLanguage.CSharp, extensions, refDlls)

    val tmp = out.targetDir.getParent.resolve("phase2-compiler-tmp")
    tmp.toFile.mkdirs()
    Files.move(out.targetDir, tmp.resolve("src"))
    Files.move(tmp, out.targetDir)

    val fullTarget = out.targetDir.toFile.getCanonicalPath
    val refs = s"/reference:${refDlls.map(dll => fullTarget + "/src/" + dll).mkString(",")}"
//    val args = domains.map(d => d.domain.id.toPackage.mkString("/")).mkString(" ")
    val cmdBuild = s"csc -target:library -out:${fullTarget}/src/lib.dll -recurse:${fullTarget}/src/*.cs $refs"
    val cmdTest = s"nunit-console ${fullTarget}/src/lib.dll"
    println(
      s"""
         |cd $fullTarget
         |$cmdBuild
         |$cmdTest
       """.stripMargin)

    val exitCodeBuild = run(out, cmdBuild, Map.empty, "cs-build")
    val exitCodeTest = run(out, cmdTest, Map.empty, "cs-test")

    exitCodeBuild == 0 && exitCodeTest == 0
  }

  private def isRunningUnderSbt: Boolean = {
    Option(System.getProperty("java.class.path")).exists(_.contains("sbt-launch.jar"))
  }

  final case class CompilerOutput(targetDir: Path, allFiles: Seq[Path])

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
    val compilerDir = runDir.resolve("phase2-compiler")

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

    val fileRefs = new File(getClass.getResource("/refs/" + language.toString).toURI).toPath
    refFiles.map(f => {
      Files.copy(fileRefs.resolve(f), compilerDir.resolve(f))
    })

    domains.foreach {
      d =>
        val rendered = new ILRenderer(d.domain).render()
        Files.write(domainsDir.resolve(s"${d.domain.id.id}.domain"), rendered.getBytes(StandardCharsets.UTF_8))
    }

    CompilerOutput(compilerDir, allFiles)
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
}
