package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan

class TheFactoryOfAllTheFactoriesDefaultImpl extends TheFactoryOfAllTheFactories {
  override def produce(plan: FinalPlan, parentContext: Locator): Locator = ???
}
