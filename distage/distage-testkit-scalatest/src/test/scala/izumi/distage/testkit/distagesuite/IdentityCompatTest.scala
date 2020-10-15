package izumi.distage.testkit.distagesuite

import izumi.distage.model.effect.QuasiEffect
import izumi.distage.testkit.scalatest.DistageSpecScalatest
import izumi.fundamentals.platform.functional.Identity

final class IdentityCompatTest extends DistageSpecScalatest[Identity] {

  "tests in identity" should {

    "start" in {
      _: QuasiEffect[Identity] =>
        assert(true)
        println("identitycompat")
    }

    "skip (should be ignored)" in {
      _: QuasiEffect[Identity] =>
        assume(false)
    }

  }

}
