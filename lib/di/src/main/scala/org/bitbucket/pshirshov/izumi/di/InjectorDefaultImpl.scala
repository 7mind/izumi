package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.model.plan.ReadyPlan


class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: DIDef): ReadyPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def produce(diPlan: ReadyPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(diPlan, parentContext)
  }
}
