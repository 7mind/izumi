package izumi.distage.testkit.spec

import izumi.distage.testkit.runner.spec.AnyWordSpec

final class NativeTestkitEngineTest extends AnyWordSpec {
  "Native testkit engine" should {
    "load typed JSON configuration" in {
      NativeTestkitFixtures.configuration()
    }
    "memoize and release a resource across four parallel bodies" in {
      NativeTestkitFixtures.memoizedParallel()
    }
  }
}
