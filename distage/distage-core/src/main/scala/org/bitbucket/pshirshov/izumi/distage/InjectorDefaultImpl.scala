package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.distage.model.plan.FinalPlan


class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: ContextDefinition): FinalPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def produce(diPlan: FinalPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(diPlan, parentContext)
  }
}
