package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import org.scalatest.WordSpec

class QuirksTest extends WordSpec {
  def boom: Int = throw new RuntimeException()

  "Discarder" should {
    "discard values effectlessly" in {
      import Quirks.Lazy._
      boom.discard()
      discard(boom)
      discard(boom, boom)
    }

    "discard values effectlessly when only discard method imported" in {
      import Quirks.Lazy.discard
      discard(boom)
      discard(boom, boom)
    }
  }
}
