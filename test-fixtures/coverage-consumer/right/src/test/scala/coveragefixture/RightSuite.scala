package coveragefixture

import izumi.distage.testkit.runner.spec.AnyWordSpec

final class RightSuite extends AnyWordSpec {
  "coverage" should {
    "execute the known branch" in {
      require(BranchWitness.choose(true) == 42)
      require(SecondWitness.choose(true) == 42)
      println("COVERAGE_PHYSICAL_BODY suite=RightSuite fork=" + ProcessHandle.current().pid())
    }
  }
}
