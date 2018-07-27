package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

trait Injector extends Planner with Producer {
  def produce(definition: ModuleBase): Locator =
    produce(plan(definition))
}
