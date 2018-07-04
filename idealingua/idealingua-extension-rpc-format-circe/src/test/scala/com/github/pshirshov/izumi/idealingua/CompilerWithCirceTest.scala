package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.translator.toscala.{CirceDerivationTranslatorExtension, CirceGenericTranslatorExtension, ScalaTranslator}
import org.scalatest.WordSpec

class CompilerWithCirceTest extends WordSpec {

  import IDLTestTools._

  "IDL compiler" should {
    "be able to compile into scala with circe-derivation" in {
      assume(IzFiles.haveExecutable("scalac"), "scalac not available")
      assert(compilesScala(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(CirceDerivationTranslatorExtension)))
    }

    "be able to compile into scala with circe-generic" in {
      assume(IzFiles.haveExecutable("scalac"), "scalac not available")
      assert(compilesScala(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(CirceGenericTranslatorExtension)))
    }
  }
}
