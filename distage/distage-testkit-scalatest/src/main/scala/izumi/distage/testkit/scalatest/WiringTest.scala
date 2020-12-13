package izumi.distage.testkit.scalatest

import izumi.distage.framework.{PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.PlanHolder

abstract class WiringTest[AppMain <: PlanHolder, Cfg <: PlanCheckConfig.Any](
  val app: AppMain with PlanHolder.Aux[AppMain#AppEffectType],
  val planCheckConfig: Cfg = PlanCheckConfig.empty,
  val checkAgainAtRuntime: Boolean = true,
)(implicit
  val planCheck: PlanCheckMaterializer[AppMain, Cfg],
  defaultModule: DefaultModule[AppMain#AppEffectType],
) extends Spec1[AppMain#AppEffectType]()(app.tagK, defaultModule)
  with WiringAssertions {

  s"Wiring check for `${planCheck.app}`" should {
    "Pass at compile-time" in {
      assert(planCheck.checkPassed)
    }

    if (checkAgainAtRuntime) {
      "Pass at runtime" in {
        planCheck.checkAgainAtRuntime().throwOnError()
      }
    }
  }

}
