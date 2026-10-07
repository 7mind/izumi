package izumi.distage.testkit.compat.scalatest

import izumi.distage.testkit.runner.spec.AnyWordSpec

class RegularScalatestTestsShouldNotInterfere extends AnyWordSpec {

  "regular scalatest tests" should {
    "work when declared in the same module as distage-testkit-scalatest tests" in {
      assert(1 == 1)
    }
  }

}
