package izumi.distage.testkit.distagesuite

import izumi.distage.testkit.runner.spec.Spec1
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.assertions.{AssertionFailure => TestFailedException}

final class ScalatestCompatTestShould extends Spec1[Identity] {

  "test" should {
    "start" in {
      intercept[TestFailedException](assert(1 == 5))
      assert(1 == 1)
      assert(1 == 1)
      assert(1 != 2)
    }
  }

}

final class ScalatestCompatTestMust extends Spec1[Identity] {

  "test" should {
    "start" in {
      intercept[TestFailedException](assert(1 == 5))
      assert(1 == 1)
      assert(1 == 1)
      assert(1 != 2)
    }
  }

}
