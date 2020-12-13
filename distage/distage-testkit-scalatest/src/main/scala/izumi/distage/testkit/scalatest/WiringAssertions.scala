package izumi.distage.testkit.scalatest

import izumi.distage.framework.{PlanCheck, PlanCheckConfig, PlanCheckMaterializer, PlanHolder}
import org.scalatest.Assertions

trait WiringAssertions { this: Assertions =>

  def assertWiring(
    app: PlanHolder,
    cfg: PlanCheckConfig.Any = PlanCheckConfig.empty,
    checkAgainAtRuntime: Boolean = true,
  )(implicit planCheckResult: PlanCheckMaterializer[app.type, cfg.type]
  ): Unit = {
    assert(planCheckResult.checkPassed)
    if (checkAgainAtRuntime) {
      planCheckResult.checkAgainAtRuntime().throwOnError()
    }
  }

  def assertWiringRuntime(app: PlanHolder, cfg: PlanCheckConfig.Any): Unit = {
    PlanCheck.runtime.assertApp(app, cfg)
  }

}
