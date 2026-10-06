package coveragefixture

import izumi.distage.testkit.runner.spec.AnyWordSpec

final class LeftSuite extends AnyWordSpec {
  "coverage" should {
    "execute the known branch" in {
      require(BranchWitness.choose(true) == 42)
      MacroWitness.exercise()
      izumi.fundamentals.assertions.AssertionFixtures.run("range")
      println("COVERAGE_PHYSICAL_BODY suite=LeftSuite fork=" + ProcessHandle.current().pid())
    }
  }
}
