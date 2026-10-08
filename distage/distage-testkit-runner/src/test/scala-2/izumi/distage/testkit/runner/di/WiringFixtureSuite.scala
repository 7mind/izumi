package izumi.distage.testkit.runner.di

import izumi.distage.framework.PlanCheckConfig
import izumi.distage.modules.DefaultModule
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.runner.TestSuite
import izumi.distage.testkit.runner.spec.SpecWiring
import izumi.fundamentals.platform.functional.Identity

private[di] object WiringFixtureSuite {
  def make(app: WiringFrontendFixtures.App, runtime: Boolean): TestSuite =
    new SpecWiring[app.type, PlanCheckConfig.Any](app, PlanCheckConfig.empty, runtime)(WiringFrontendFixtures.materializer(app, passed = true), implicitly[DefaultModule[Identity]]) {
      override protected def config: TestConfig = WiringFrontendFixtures.configuration
    }
}
