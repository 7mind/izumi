package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase1.{Impl1, JustTrait}
import com.github.pshirshov.izumi.distage.fixtures.HigherKindCases.HigherKindsCase1.OptionT
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
import com.github.pshirshov.izumi.functional.Renderable._
import distage.ModuleDef
import org.scalatest.WordSpec

trait T1[A, B]

class K1[F[_, _]]

class CompactPlanFormatterTest extends WordSpec with MkInjector {
  "PlanFormatterTest should produce short class names if it's unique in plan" in {
    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(new ModuleDef {
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

