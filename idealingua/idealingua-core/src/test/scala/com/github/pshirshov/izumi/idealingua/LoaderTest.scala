package com.github.pshirshov.izumi.idealingua

import org.scalatest.WordSpec


class LoaderTest extends WordSpec {

  "IL loader" should {
    "parse all test domains" in {
      val defs = IDLTestTools.loadDefs()
      assert(defs.nonEmpty)
      defs.foreach {
        d =>
          assert(d.domain.types.nonEmpty)
      }
    }
  }
}

