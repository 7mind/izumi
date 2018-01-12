package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.ReadyPlan

class TheFactoryOfAllTheFactoriesDefaultImpl extends TheFactoryOfAllTheFactories {
  override def produce(plan: ReadyPlan, parentContext: Locator): Locator = ???
}
