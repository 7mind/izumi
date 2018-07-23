package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.{AbstractPlan, OrderedPlan}
import com.github.pshirshov.izumi.distage.model.{Injector, Locator, Planner, TheFactoryOfAllTheFactories}

class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: ModuleBase): OrderedPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan = {
    parentContext.get[Planner].merge(a, b)
  }

  override def produce(plan: OrderedPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(plan, parentContext)
  }
}
