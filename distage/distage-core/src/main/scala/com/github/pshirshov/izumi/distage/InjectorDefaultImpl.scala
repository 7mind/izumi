package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.{Injector, Locator, Planner, TheFactoryOfAllTheFactories}


class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: ModuleBase): FinalPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def produce(diPlan: FinalPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(diPlan, parentContext)
  }
}
