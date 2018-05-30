package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class CompilerTest extends WordSpec {

  import IDLTestTools._

  "IDL compiler" should {
    "be able to compile into scala" in {
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into typescript" in {
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into golang" in {
      assert(compilesGolang(getClass.getSimpleName, loadDefs()))
    }
  }
}

