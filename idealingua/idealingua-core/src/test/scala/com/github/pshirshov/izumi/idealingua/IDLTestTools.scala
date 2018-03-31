package com.github.pshirshov.izumi.idealingua

import java.io.{File, IOException}
import java.lang.management.ManagementFactory
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.idealingua.il.ILRenderer
import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinition
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{IDLFailure, IDLSuccess}
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage, TranslatorExtension}

@ExposedTestScope
object IDLTestTools {
  def loadDefs(): Seq[DomainDefinition] = {
    val src = new File(getClass.getResource("/defs").toURI).toPath
    val loader = new LocalModelLoader(src, Seq.empty)
    val loaded = loader.load()
    assert(loaded.size == 6)
    loaded.foreach {
      d=>
      println(new ILRenderer(d).render())
    }
    loaded
  }

  def compiles(id: String, domains: Seq[DomainDefinition]): Boolean = {
    compiles(id, domains, Seq.empty)
  }

  def compiles(id: String, domains: Seq[DomainDefinition], extensions: Seq[TranslatorExtension]): Boolean = {
    val tmpdir = Paths.get("target")
    val runPrefix = s"idl-${ManagementFactory.getRuntimeMXBean.getStartTime}"
    val runDir = tmpdir.resolve(s"$runPrefix-${System.currentTimeMillis()}-$id")

    tmpdir
      .toFile
      .listFiles()
      .toList
      .filter(f => f.isDirectory && f.getName.startsWith("idl-") && !f.getName.startsWith(runPrefix))
      .foreach {
        f =>
          remove(f.toPath)
      }

    val allFiles = domains.flatMap {
      domain =>
        val compiler = new IDLCompiler(domain)
        compiler.compile(runDir.resolve(domain.id.toPackage.mkString(".")), IDLCompiler.CompilerOptions(language = IDLLanguage.Scala, extensions)) match {
          case IDLSuccess(files) =>
            assert(files.toSet.size == files.size)
            files

          case f: IDLFailure =>
            throw new IllegalStateException(s"Does not compile: $f")
        }
    }

      {
        val ctarget = runDir.resolve("scalac")
        ctarget.toFile.mkdirs()

        import scala.tools.nsc.{Global, Settings}
        val settings = new Settings()
        settings.d.value = ctarget.toString
        settings.feature.value = true
        settings.warnUnused.add("_")
        settings.embeddedDefaults(this.getClass.getClassLoader)

        val isSbt = Option(System.getProperty("java.class.path")).exists(_.contains("sbt-launch.jar"))
        if (!isSbt) {
          settings.usejavacp.value = true
        }

        val g = new Global(settings)
        val run = new g.Run
        run.compile(allFiles.map(_.toFile.getCanonicalPath).toList)
        run.runIsAt(run.jvmPhase.next)
      }

  }

  def remove(root: Path): Unit = {
    val _  = Files.walkFileTree(root, new SimpleFileVisitor[Path] {
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
