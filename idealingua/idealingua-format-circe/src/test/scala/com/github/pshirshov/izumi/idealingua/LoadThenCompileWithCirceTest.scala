package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.translator.CirceTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import org.scalatest.WordSpec


class LoadThenCompileWithCirceTest extends WordSpec {

  import IDLTestTools._

  "IL loader" should {
    "load & parse domain definition" in {
      assert(compiles(getClass.getSimpleName, loadDefs(), ScalaTranslator.defaultExtensions ++ Seq(CirceTranslatorExtension)))
    }
  }
}
