package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class LoadThenCompileTest extends WordSpec {

  import IDLTestTools._

  "IL loader" should {
    "load & parse domain definition" in {
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }
  }
}

