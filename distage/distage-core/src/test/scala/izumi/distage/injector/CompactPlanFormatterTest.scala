package izumi.distage.injector

import izumi.distage.fixtures.BasicCases.BasicCase1.{Impl1, JustTrait}
import izumi.distage.fixtures.HigherKindCases.HigherKindsCase1.OptionT
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.CompactPlanFormatter._
import izumi.functional.Renderable._
import distage.ModuleDef
import org.scalatest.WordSpec

trait T1[A, B]

class K1[F[_, _]]

class CompactPlanFormatterTest extends WordSpec with MkInjector {
  "PlanFormatterTest should produce short class names if it's unique in plan" in {
    val injector = mkInjector()
    val plan = injector.plan(PlannerInput.noGc(new ModuleDef {
      make[JustTrait].from[Impl1]
      make[OptionT[scala.Either[Nothing, ?], Unit]].from(OptionT[Either[Nothing, ?], Unit](Right(None)))
      make[K1[T1]].from(new K1[T1]{})
    }))

    val formatted = plan.render()
    assert(!formatted.contains(classOf[Impl1].getName))
    assert(formatted.contains("Impl1"))

    assert(formatted.contains("OptionT[Either[Nothing,?],Unit]"))
  }
}

