package izumi.fundamentals.platform.assertions

import izumi.distage.testkit.runner.spec.AnyWordSpec
import izumi.fundamentals.assertions.AssertionFailure

final class PlatformGuardAssertionsTest extends AnyWordSpec with ScalatestGuards {
  "Platform guards" should {
    "evaluate a failing body exactly once" in {
      var evaluations = 0
      broken {
        evaluations += 1
        throw new IllegalStateException("expected failure")
      }
      assert(evaluations == 1)
    }

    "reject a successful body after evaluating it exactly once" in {
      var evaluations = 0
      intercept[AssertionFailure](broken { evaluations += 1 })
      assert(evaluations == 1)
    }
  }
}
