package izumi.distage.testkit.scalatest

import izumi.distage.framework.{CheckableApp, PlanCheckConfig, PlanCheckMaterializer}
import izumi.distage.modules.DefaultModule

abstract class SpecWiring[F[_], AppMain <: CheckableApp { type AppEffectType[A] = F[A] }, Cfg <: PlanCheckConfig.Any](
  val app: AppMain,
  val cfg: Cfg = PlanCheckConfig.empty,
  val checkAgainAtRuntime: Boolean = true,
)(implicit
  val planCheck: PlanCheckMaterializer[AppMain, Cfg],
  defaultModule: DefaultModule[F],
) extends Spec1[F]()(app.tagK, defaultModule)
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
