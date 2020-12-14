package izumi.distage.testkit.distagesuite.compiletime

import izumi.distage.framework.{PlanCheck, PlanCheckConfig}
import izumi.distage.roles.test.TestEntrypointPatchedLeak

import scala.annotation.nowarn

@nowarn("msg=early initializers are deprecated")
object StandaloneWiringTestMain extends {
  private val cfg =
    PlanCheckConfig(
      "* -failingrole01 -failingrole02",
      "mode:test",
    )
} with PlanCheck.Main(TestEntrypointPatchedLeak, cfg)

@nowarn("msg=early initializers are deprecated")
object StandaloneWiringTestMain2 extends {
  private val cfg = PlanCheckConfig(
    "* -failingrole01 -failingrole02",
    "mode:test",
  )
} with TestEntrypointPatchedLeak.PlanCheck(cfg)
