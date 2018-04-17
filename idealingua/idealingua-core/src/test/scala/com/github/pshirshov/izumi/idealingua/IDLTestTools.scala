package com.github.pshirshov.izumi.idealingua

import java.io.{File, IOException}
import java.lang.management.ManagementFactory
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.il.renderer.ILRenderer
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainDefinition
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{IDLFailure, IDLSuccess}
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage, TranslatorExtension}


@ExposedTestScope
object IDLTestTools {
  def loadDefs(): Seq[DomainDefinition] = {
    val src = new File(getClass.getResource("/defs").toURI).toPath
    val loader = new LocalModelLoader(src, Seq.empty)
    val loaded = loader.load()
    val loadableCount = loader.enumerate().count(_._1.toString.endsWith(LocalModelLoader.domainExt))
    assert(loaded.size == loadableCount, s"expected $loadableCount domains")
    loaded.foreach {
      d =>
        println(new ILRenderer(d).render())
    }
    loaded
  }

  def compilesScala(id: String, domains: Seq[DomainDefinition], extensions: Seq[TranslatorExtension] = ScalaTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Scala, extensions)

    val ctarget = out.targetDir.resolve("scalac")
    ctarget.toFile.mkdirs()

    import scala.tools.nsc.{Global, Settings}
    val settings = new Settings()
    settings.d.value = ctarget.toString
    settings.feature.value = true
    settings.warnUnused.add("_")
    settings.embeddedDefaults(this.getClass.getClassLoader)

    if (!isRunningUnderSbt) {
      settings.usejavacp.value = true
    }

    val g = new Global(settings)
    val run = new g.Run
    run.compile(out.allFiles.map(_.toFile.getCanonicalPath).toList)
    run.runIsAt(run.jvmPhase.next)
  }

  private def isRunningUnderSbt: Boolean = {
    Option(System.getProperty("java.class.path")).exists(_.contains("sbt-launch.jar"))
  }

  def compilesTypeScript(id: String, domains: Seq[DomainDefinition], extensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions): Boolean = {
    val out = compiles(id, domains, IDLLanguage.Typescript, extensions)
    out.allFiles.nonEmpty
  }

  case class CompilerOutput(targetDir: Path, allFiles: Seq[Path])

  private def compiles(id: String, domains: Seq[DomainDefinition], language: IDLLanguage, extensions: Seq[TranslatorExtension]): CompilerOutput = {
    val tmpdir = Paths.get("target")

    // TODO: clashes still may happen in case of parallel runs with the same ID
    val stablePrefix = s"idl-${language.toString}-$id"
    val vmPrefix = s"$stablePrefix-${ManagementFactory.getRuntimeMXBean.getStartTime}"
    val dirPrefix = s"$vmPrefix-${System.currentTimeMillis()}"
    val runDir = tmpdir.resolve(dirPrefix)

    tmpdir
      .toFile
      .listFiles()
      .toList
      .filter(f => f.isDirectory && f.getName.startsWith(stablePrefix) && !f.getName.startsWith(vmPrefix))
      .foreach {
        f =>
          remove(f.toPath)
      }

    val allFiles: Seq[Path] = domains.flatMap {
      domain =>
        val compiler = new IDLCompiler(domain)
        compiler.compile(runDir.resolve(domain.id.toPackage.mkString(".")), IDLCompiler.CompilerOptions(language, extensions)) match {
          case IDLSuccess(files) =>
            assert(files.toSet.size == files.size)
            files

          case f: IDLFailure =>
            throw new IllegalStateException(s"Does not compile: $f")
        }
    }

    CompilerOutput(runDir, allFiles)
  }

  def remove(root: Path): Unit = {
    val _ = Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }

    })


  }
}
