package izumi.distage.testkit.distagesuite

import izumi.distage.model.effect.DIEffect
import izumi.distage.testkit.scalatest.Spec1
import izumi.fundamentals.platform.functional.Identity

final class IdentityCompatTest extends Spec1[Identity] {

  "tests in identity" should {

    "start" in {
      _: DIEffect[Identity] =>
        assert(true)
        println("identitycompat")
    }

    "skip (should be ignored)" in {
      _: DIEffect[Identity] =>
        assume(false)
    }

  }

}
