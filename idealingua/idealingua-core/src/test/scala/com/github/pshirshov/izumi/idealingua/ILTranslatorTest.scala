package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class ILTranslatorTest extends WordSpec {

  import IDLTestTools._

  "Intermediate language translator" should {
    "be able to produce scala source code" in {
      assert(compilesScala(getClass.getSimpleName, Seq(Model01.domain, Model02.domain)))
    }
    "be able to produce typescript source code" in {
      assert(compilesTypeScript(getClass.getSimpleName, Seq(Model01.domain, Model02.domain)))
    }
  }
}
