package com.github.pshirshov.izumi.idealingua

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef
import com.github.pshirshov.izumi.idealingua.model.finaldef._
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl
import org.scalatest.WordSpec

import scala.tools.nsc.MainClass



class IDLTest extends WordSpec {


  "IDL rendered" should {
    "be able to produce scala source code" in {
      assert(compiles(Model01.domain))
    }

    
  }

  private def compiles(d: DomainDefinition): Boolean = {
    val modules = new FinalTranslatorScalaImpl().translate(d)

    val top = Seq("target", "idl-" + System.currentTimeMillis())
    val files = modules.map {
      module =>

        val path = top ++ module.id.path :+ module.id.name
        val mpath = path.mkString("/")
        println(mpath)
        println(module.content)
        println()
        val xpath = Paths.get(path.head, path.tail: _*)
        xpath.getParent.toFile.mkdirs()
        Files.write(xpath, module.content.getBytes(StandardCharsets.UTF_8))
        mpath
    }

    println(files.toList)
    assert(files.toSet.size == files.size)

    {
      import scala.tools.nsc.{Global, Settings}
      val settings = new Settings()
      settings.usejavacp.value = true
      val g = new Global(settings)
      val run = new g.Run
      run.compile(files.toList)
      run.runIsAt(run.jvmPhase.next)
    }

    //      {
    //        import scala.tools.nsc.Settings
    //        import scala.tools.nsc.{Global, Settings}
    //
    //        val settings: Settings = new Settings()
    //        settings.usejavacp.value = true
    //
    //        val global = new Global(settings)
    //        new MainClass().newCompiler().newCompilationUnit("object Test {}", "test.scala")
    //      }
  }
}
