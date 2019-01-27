package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

trait Injector extends Planner with Producer {
  def produce(input: PlannerInput): Locator =
    produceUnsafe(plan(input))

  final def produce(input: ModuleBase): Locator = {
    produce(PlannerInput(input))
  }
}
