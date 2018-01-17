package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan


class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: ContextDefinition): FinalPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def produce(diPlan: FinalPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(diPlan, parentContext)
  }
}
