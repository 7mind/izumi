package izumi.distage.testkit.distagesuite.compiletime

import com.github.pshirshov.test.plugins.StaticTestMain
import izumi.distage.framework.PlanCheckConfig
import izumi.distage.testkit.scalatest.WiringTest
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.language.literals.LiteralString

class StandaloneWiringTest
  extends WiringTest(
    StaticTestMain,
    PlanCheckConfig(
      roles = LiteralString("statictestrole"),
      excludeActivations = LiteralString(""),
      config = LiteralString("check-test-good.conf"),
    ),
  ) {

  "And again" in {
    planCheck.checkAtRuntime().throwOnError().discard()
  }

  "And again 2" in {
    assertWiring(
      StaticTestMain,
      planCheckConfig,
    )
  }

}
