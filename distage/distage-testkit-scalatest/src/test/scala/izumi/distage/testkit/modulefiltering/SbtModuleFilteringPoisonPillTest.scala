package izumi.distage.testkit.modulefiltering

import izumi.distage.testkit.scalatest.SpecIdentity

import java.util.concurrent.atomic.AtomicReference

object SbtModuleFilteringPoisonPillTest {
  val poisonPillTestsLaunched: AtomicReference[Int] = new AtomicReference(0)
}

open class SbtModuleFilteringPoisonPillTest extends SpecIdentity {

  "SBT test module filtering fix" should {

    "prevent `sbt test` task in `distage-testkit-scalatest-sbt-module-filtering-test` from launching test classes defined in `distage-testkit-scalatest` test scope" in {
      val testsLaunched = SbtModuleFilteringPoisonPillTest.poisonPillTestsLaunched.updateAndGet(_ + 1)
      assert(testsLaunched == 1)
    }

  }

}
