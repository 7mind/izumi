package izumi.distage.testkit.distagesuite

import izumi.distage.testkit.scalatest.Spec1
import izumi.functional.bio.IO1
import izumi.fundamentals.platform.functional.Identity

final class IdentityCompatTest extends Spec1[Identity] {

  "tests in identity" should {

    "start" in {
      (_: IO1[Identity]) =>
        assert(true)
    }

    "skip (should be ignored due to `assume`)" in {
      (_: IO1[Identity]) =>
        assume(false)
        assert(false)
    }

  }

}
