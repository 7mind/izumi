package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan

trait TheFactoryOfAllTheFactories {
  def produce(plan: FinalPlan, parentContext: Locator): Locator
}
