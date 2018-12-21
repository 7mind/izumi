package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase1.{Impl1, JustTrait}
import com.github.pshirshov.izumi.distage.model.plan.CompactPlanFormatter._
import com.github.pshirshov.izumi.functional.Renderable._
import distage.ModuleDef
import org.scalatest.WordSpec

class CompactPlanFormatterTest extends WordSpec with MkInjector {
  "PlanFormatterTest should produce short class names if it's unique in plan" in {
    val injector = mkInjector()
    val plan = injector.plan(new ModuleDef {
      many[JustTrait]
        .add(new Impl1)
    })

    val formatted = plan.render()
    assert(!formatted.contains(classOf[Impl1].getName) && formatted.contains("Impl1"))
  }
}

