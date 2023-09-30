package izumi.distage.testkit.distagesuite

import izumi.functional.quasi.QuasiIO
import izumi.distage.testkit.scalatest.Spec1
import izumi.fundamentals.platform.functional.Identity

final class IdentityCompatTest extends Spec1[Identity] {

  "tests in identity" should {

    "start" in {
      (_: QuasiIO[Identity]) =>
        assert(true)
    }

    "skip (should be ignored due to `assume`)" in {
      (_: QuasiIO[Identity]) =>
        assume(false)
    }

  }

}
