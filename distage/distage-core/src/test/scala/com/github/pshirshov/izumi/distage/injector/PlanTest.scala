package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase1.{Impl1, JustTrait}
import distage.{Injector, ModuleDef}
import org.scalatest.WordSpec

class PlanTest extends WordSpec with MkInjector {
  "OrderedPlan has an human-readable toString impl" in {
    val injector = mkInjector()
    val plan = injector.plan(new ModuleDef {
      many[JustTrait]
        .add(new Impl1)
    })
    println(plan.toString)
  }
}
