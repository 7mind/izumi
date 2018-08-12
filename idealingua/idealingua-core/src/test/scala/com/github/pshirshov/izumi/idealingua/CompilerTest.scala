package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import org.scalatest.WordSpec


class CompilerTest extends WordSpec {

  import IDLTestTools._

  "IDL compiler" should {
    "be able to compile into scala" in {
      assume(IzFiles.haveExecutables("scalac"), "scalac not available")
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }

    "be able to compile into typescript" in {
      assume(IzFiles.haveExecutables("tsc"), "tsc not available")
      assume(IzFiles.haveExecutables("npm"), "tsc not available")
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs(), scoped = false))
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs(), scoped = true))
    }

    "be able to compile into golang" in {
      assume(IzFiles.haveExecutables("go"), "go not available")
      assert(compilesGolang(getClass.getSimpleName, loadDefs(), scoped = false))
      assert(compilesGolang(getClass.getSimpleName, loadDefs(), scoped = true))
    }

    "be able to compile into csharp" in {
      assume(IzFiles.haveExecutables("csc", "nunit-console"), "csc not available")
      assert(compilesCSharp(getClass.getSimpleName, loadDefs()))
    }

//    "be able to compile into scala with circe-derivation" in {
//      assume(IzFiles.haveExecutables("scalac"), "scalac not available")
//      assert(compilesScala(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(CirceDerivationTranslatorExtension)))
//    }
//
//    "be able to compile into scala with circe-generic" in {
//      assume(IzFiles.haveExecutables("scalac"), "scalac not available")
//      assert(compilesScala(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(CirceGenericTranslatorExtension)))
//    }
  }
}

