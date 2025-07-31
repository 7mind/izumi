package izumi.distage.testkit.autosets

import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.AutoSetHook
import izumi.distage.plugins.{BootstrapPluginDef, PluginConfig, PluginDef}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec1
import izumi.fundamentals.platform.functional.Identity

trait TestTrait

trait TestService
class TestServiceImpl extends TestService with TestTrait

object AutosetTestModule extends PluginDef {
  make[TestService].from[TestServiceImpl]
  many[TestTrait]
}

object AutosetTestBsModule extends BootstrapPluginDef {
  many[PlanningHook].add(AutoSetHook[TestTrait])

}

final class AutosetTest extends Spec1[Identity] {

  "autosets" should {
    "be compatible with testkit" in {
      (t: Set[TestTrait], impl: TestService) =>
        assert(t.toSet[AnyRef].contains(impl: AnyRef))
    }
  }

  override protected def config: TestConfig = super.config.copy(
    pluginConfig = PluginConfig.const(Seq(AutosetTestModule)),
    bootstrapPluginConfig = PluginConfig.const(Seq(AutosetTestBsModule)),
  )
}
