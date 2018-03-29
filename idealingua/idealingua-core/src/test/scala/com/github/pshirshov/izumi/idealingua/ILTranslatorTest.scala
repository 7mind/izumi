package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class ILTranslatorTest extends WordSpec {

  import IDLTestTools._

  "Intermediate language translator" should {
    "be able to produce scala source code" in {
      assert(compiles(getClass.getSimpleName, Seq(Model01.domain, Model02.domain)))
    }
  }

}




