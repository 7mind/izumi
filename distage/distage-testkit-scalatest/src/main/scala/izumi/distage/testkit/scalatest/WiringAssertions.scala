package izumi.distage.testkit.scalatest

import izumi.distage.framework.{CheckableApp, PlanCheck, PlanCheckConfig, PlanCheckMaterializer}
import org.scalatest.Assertions

trait WiringAssertions { this: Assertions =>

  def assertWiringCompileTime(
    app: CheckableApp,
    cfg: PlanCheckConfig.Any = PlanCheckConfig.empty,
    checkAgainAtRuntime: Boolean = true,
  )(implicit planCheckResult: PlanCheckMaterializer[app.type, cfg.type]
  ): Unit = {
    assert(planCheckResult.checkPassed)
    if (checkAgainAtRuntime) {
      planCheckResult.checkAgainAtRuntime().throwOnError()
    }
  }

  def assertWiringRuntime(app: CheckableApp, cfg: PlanCheckConfig.Any): Unit = {
    PlanCheck.runtime.assertApp(app, cfg)
  }

}
