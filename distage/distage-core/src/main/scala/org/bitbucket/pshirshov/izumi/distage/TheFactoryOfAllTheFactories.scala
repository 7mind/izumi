package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.model.plan.FinalPlan

trait TheFactoryOfAllTheFactories {
  def produce(plan: FinalPlan, parentContext: Locator): Locator
}
