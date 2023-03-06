package izumi.distage.testkit.scalatest

import izumi.distage.framework.{CheckableApp, PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.modules.DefaultModule
import izumi.functional.quasi.QuasiIO

abstract class SpecWiring[AppMain <: CheckableApp, Cfg <: PlanCheckConfig.Any](
  val app: AppMain with CheckableApp.Aux[AppMain#AppEffectType],
  val cfg: Cfg = PlanCheckConfig.empty,
  val checkAgainAtRuntime: Boolean = true,
)(implicit
  val planCheck: PlanCheckMaterializer[AppMain, Cfg],
  defaultModule: DefaultModule[AppMain#AppEffectType],
  F: QuasiIO[AppMain#AppEffectType],
) extends Spec1[AppMain#AppEffectType]()(app.tagK, defaultModule, F)
  with WiringAssertions {

  s"Wiring check for `${planCheck.app.getClass.getCanonicalName}`" should {
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
