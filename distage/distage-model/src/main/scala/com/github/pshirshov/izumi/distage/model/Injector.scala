package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase

trait Injector extends Planner with Producer {
  def run(definition: ModuleBase): Locator =
    produce(plan(definition))
}
