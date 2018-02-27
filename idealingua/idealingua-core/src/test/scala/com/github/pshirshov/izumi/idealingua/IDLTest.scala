package com.github.pshirshov.izumi.idealingua

import java.nio.file.Paths

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
    val allFiles = domains.flatMap {
      domain =>
        val compiler = new IDLCompiler(domain)
        compiler.compile(Paths.get("target", "idl-" + System.currentTimeMillis()), IDLCompiler.CompilerOptions(language = IDLLanguage.Scala)) match {
          case IDLSuccess(files) =>
            assert(files.toSet.size == files.size)
            files

          case f: IDLFailure =>
            throw new IllegalStateException(s"Does not compile")
        }
    }

    {
      import scala.tools.nsc.{Global, Settings}
      val settings = new Settings()
      settings.embeddedDefaults(getClass.getClassLoader)
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


}

