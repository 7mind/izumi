package com.github.pshirshov.izumi.distage.model

trait Injector extends Planner with Producer {
  def produce(input: PlannerInput): Locator =
    produce(plan(input))
}
