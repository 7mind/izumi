package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.translator.toscala.{CirceDerivationTranslatorExtension, CirceGenericTranslatorExtension, ScalaTranslator}
import org.scalatest.WordSpec

class LoadThenCompileWithCirceTest extends WordSpec {

  import IDLTestTools._

  "IL loader" should {
    "load & parse domain definition using circe-derivation" in {
      assert(compilesScala(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(new CirceDerivationTranslatorExtension)))
    }

    "load & parse domain definition using circe-generic" ignore {
      assert(compilesScala(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(new CirceGenericTranslatorExtension)))
    }
  }
}
