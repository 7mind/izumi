package com.github.pshirshov.izumi.idealingua

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._

import com.github.pshirshov.izumi.idealingua.model.il._
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{IDLFailure, IDLSuccess}
import com.github.pshirshov.izumi.idealingua.translator.{IDLCompiler, IDLLanguage}
import org.scalatest.WordSpec


class IDLTest extends WordSpec {

  import IDLTest._

  "IDL renderer" should {
    "be able to produce scala source code" in {
      assert(compiles(Seq(Model01.domain, Model02.domain)))
    }
  }


}

object IDLTest {
  def compiles(domains: Seq[DomainDefinition]): Boolean = {
    val tmpdir = Paths.get("target")
    tmpdir
      .toFile
      .listFiles()
      .toList
      .filter(f => f.getName.startsWith("idl-") && f.isDirectory)
      .foreach {
        f =>
          remove(f.toPath)
      }

    val runDir = tmpdir.resolve("idl-" + System.currentTimeMillis())

    val allFiles = domains.flatMap {
      domain =>
        val compiler = new IDLCompiler(domain)
        compiler.compile(runDir.resolve(domain.id.toPackage.mkString(".")), IDLCompiler.CompilerOptions(language = IDLLanguage.Scala)) match {
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


