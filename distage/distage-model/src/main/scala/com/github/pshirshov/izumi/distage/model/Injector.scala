package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait Injector extends Planner with Producer {
  def produce(input: PlannerInput): Locator =
    produceUnsafe(plan(input))

  final def produce(input: ModuleBase, roots: Set[DIKey] = Set.empty): Locator = {
    produce(PlannerInput(input, roots))
  }
}
