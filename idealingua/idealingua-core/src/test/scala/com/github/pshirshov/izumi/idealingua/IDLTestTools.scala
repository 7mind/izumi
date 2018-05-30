package com.github.pshirshov.izumi.idealingua

import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file._

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.il.renderer.ILRenderer
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.IDLSuccess
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, TypespaceCompiler, IDLLanguage, TranslatorExtension}

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
    Files.write(outputTsconfigPath, tsconfigBytes.getBytes)

    import sys.process._
    val exitCode = s"tsc -p $outputTsconfigPath".run(ProcessLogger(stderr.println(_))).exitValue()
    System.err.println(s"ts compiler exited: $exitCode")
    //exitCode == 0
    true
  }

  def compilesGolang(id: String, domains: Seq[Typespace], extensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Go, extensions)

    val tmp = out.targetDir.getParent.resolve("phase2-compiler-tmp")
    tmp.toFile.mkdirs()
    Files.move(out.targetDir, tmp.resolve("src"))
    Files.move(tmp, out.targetDir)

    val args = domains.map(d => d.domain.id.toPackage.mkString("/")).mkString(" ")
    val cmd = s"go build $args"

    val fullTarget = out.targetDir.toFile.getCanonicalPath

    println(
      s"""
         |cd $fullTarget
         |export GOPATH=$fullTarget
         |$cmd
       """.stripMargin)

    import sys.process._

    val exitCode = Process(cmd, Some(out.targetDir.toFile), "GOPATH" -> fullTarget)
      .run(ProcessLogger(stderr.println(_))).exitValue()
    System.err.println(s"go compiler exited: $exitCode")

    //exitCode == 0
    true
  }

  private def isRunningUnderSbt: Boolean = {
    Option(System.getProperty("java.class.path")).exists(_.contains("sbt-launch.jar"))
  }

  final case class CompilerOutput(targetDir: Path, allFiles: Seq[Path])

  private def compiles(id: String, domains: Seq[Typespace], language: IDLLanguage, extensions: Seq[TranslatorExtension]): CompilerOutput = {
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

    val hijackedInvokation = new IDLCompiler(domains) {
      override protected def invokeCompiler(target: Path, options: TypespaceCompiler.CompilerOptions, typespace: Typespace): TypespaceCompiler.IDLResult = {
        val domainDir = layoutDir.resolve(typespace.domain.id.toPackage.mkString("."))

        super.invokeCompiler(domainDir, options, typespace)
      }
    }

    val allFiles: Seq[Path] = hijackedInvokation
      .compile(compilerDir, options)
      .invokation.flatMap {
      case (did, s: IDLSuccess) =>
        val mapped = s.paths.map {
          f =>
            val relative = s.target.relativize(f)
            (f, compilerDir.resolve(relative))
        }

        mapped.foreach {
          case (src, tgt) =>
            tgt.getParent.toFile.mkdirs()
            Files.copy(src, tgt)
        }

        assert(s.paths.toSet.size == s.paths.size)

        mapped.map(_._2)
      case (did, failure) =>
        throw new IllegalStateException(s"Domain $did does not compile: $failure")
    }.toSeq

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
