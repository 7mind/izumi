package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan

trait TheFactoryOfAllTheFactories {
  def produce(plan: FinalPlan, parentContext: Locator): Locator
}
