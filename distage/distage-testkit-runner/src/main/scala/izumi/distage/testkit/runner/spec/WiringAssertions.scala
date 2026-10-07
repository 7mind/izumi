package izumi.distage.testkit.runner.spec

import izumi.distage.framework.{CheckableApp, PlanCheck, PlanCheckConfig, PlanCheckMaterializer}

trait WiringAssertions { this: TestAssertions =>

  def assertWiringCompileTime(
    app: CheckableApp,
    cfg: PlanCheckConfig.Any,
    checkAgainAtRuntime: Boolean,
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
