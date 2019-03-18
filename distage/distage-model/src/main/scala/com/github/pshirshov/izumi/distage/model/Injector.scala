package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait Injector extends Planner with Producer {
  final def produceUnsafe(input: PlannerInput): Locator = {
    produceUnsafe(plan(input))
  }

  final def produceUnsafe(input: ModuleBase, roots: Set[DIKey] = Set.empty): Locator = {
    produceUnsafe(plan(PlannerInput(input, roots)))
  }
}
