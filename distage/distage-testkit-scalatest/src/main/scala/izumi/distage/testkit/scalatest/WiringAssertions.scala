package izumi.distage.testkit.scalatest

import izumi.distage.framework.PlanCheck.{defaultCheckConfig, defaultPrintBindings}
import izumi.distage.framework.{PlanCheck, PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.roles.PlanHolder
import org.scalatest.Assertions

trait WiringAssertions { this: Assertions =>

  def assertWiring(
    app: PlanHolder,
    cfg: PlanCheckConfig.Any,
    checkAgainAtRuntime: Boolean = true,
  )(implicit planCheck: PlanCheckMaterializer[app.type, cfg.type]
  ): Unit = {
    assert(planCheck.checkPassed)
    if (checkAgainAtRuntime) {
      planCheck.checkAtRuntime().throwOnError()
    }
  }

  def assertWiringRuntime(
    app: PlanHolder,
    roles: String = "*",
    excludeActivations: String = "",
    config: String = "*",
    checkConfig: Boolean = defaultCheckConfig,
    printBindings: Boolean = defaultPrintBindings,
  ): Unit = {
    PlanCheck.runtime
      .checkApp(
        app = app,
        roles = roles,
        excludeActivations = excludeActivations,
        config = config,
        checkConfig = checkConfig,
        printBindings = printBindings,
      ).throwOnError()
  }

}
