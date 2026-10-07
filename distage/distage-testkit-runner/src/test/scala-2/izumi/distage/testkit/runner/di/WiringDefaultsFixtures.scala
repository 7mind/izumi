package izumi.distage.testkit.runner.di

import izumi.distage.framework.{PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.runner.TestSuite
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.runner.spec.{SpecWiring, TestAssertions, WiringAssertions}
import izumi.fundamentals.platform.functional.Identity

private[di] object WiringDefaultsFixtures extends WiringDefaultsControls {
  def withConfig(app: WiringFrontendFixtures.App): TestSuite =
    new SpecWiring[app.type, PlanCheckConfig.Any](app, PlanCheckConfig.empty)(WiringFrontendFixtures.materializer(app, passed = true), implicitly[DefaultModule[Identity]]) {
      override protected def config: TestConfig = WiringFrontendFixtures.configuration
    }

  def withDefaults(app: WiringFrontendFixtures.App): TestSuite =
    new SpecWiring[app.type, PlanCheckConfig.Any](app)(WiringFrontendFixtures.materializer(app, passed = true), implicitly[DefaultModule[Identity]]) {
      override protected def config: TestConfig = WiringFrontendFixtures.configuration
    }

  def assertion(app: WiringFrontendFixtures.App): Unit = {
    val cfg = PlanCheckConfig.empty
    val plan = PlanCheckMaterializer[app.type, cfg.type](true, Seq.empty, app, "*", "", "*", Some(false), Some(false), Some(false))
    val assertions = new TestAssertions with WiringAssertions {}
    assertions.assertWiringCompileTime(app, cfg)(plan)
  }
}
