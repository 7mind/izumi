package izumi.fundamentals.platform

import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.unused
import org.scalatest.wordspec.AnyWordSpec

class QuirksTest extends AnyWordSpec {
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

    "ignore values with unused annotation" in {
      def x(@unused x: => Int): Unit = ()
      x(boom)
    }
  }
}


