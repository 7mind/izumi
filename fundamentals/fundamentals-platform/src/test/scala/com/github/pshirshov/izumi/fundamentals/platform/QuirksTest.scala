package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import org.scalatest.WordSpec

class QuirksTest extends WordSpec {
  def boom: Int = throw new RuntimeException()

  "Discarder" should {
    "forget values effectlessly" in {
      boom.forget
      forget(boom)
      forget(boom, boom)
    }

    "forget values effectlessly when only discard method imported" in {
      forget(boom)
      forget(boom, boom)
    }

    "discard values effectfully" in {
      intercept[RuntimeException](boom.discard())
      intercept[RuntimeException](discard(boom))
      intercept[RuntimeException](discard(boom, boom))

    }
  }
}
