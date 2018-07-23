package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan

trait TheFactoryOfAllTheFactories {
  def produce(plan: OrderedPlan, parentContext: Locator): Locator
}
