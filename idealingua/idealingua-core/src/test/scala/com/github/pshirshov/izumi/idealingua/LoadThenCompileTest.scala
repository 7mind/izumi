package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class LoadThenCompileTest extends WordSpec {

  import IDLTestTools._

  "IL loader" should {
    "load & parse domain definition in scala" in {
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }

    "load & parse domain definition in typescript" in {
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs()))
    }
  }
}

