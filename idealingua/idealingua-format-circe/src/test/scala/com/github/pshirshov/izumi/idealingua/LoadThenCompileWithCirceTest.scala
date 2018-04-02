package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.translator.{CirceTranslatorExtension, IDLLanguage}
import org.scalatest.WordSpec


class LoadThenCompileWithCirceTest extends WordSpec {

  import IDLTestTools._

  "IL loader" should {
    "load & parse domain definition" in {
      assert(compiles(getClass.getSimpleName, loadDefs(), IDLLanguage.Scala, Seq(CirceTranslatorExtension)))
    }
  }
}
