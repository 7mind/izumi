package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.ReadyPlan

trait TheFactoryOfAllTheFactories {
  def produce(plan: ReadyPlan, parentContext: Locator): Locator
}
