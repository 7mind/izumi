package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.provisioning.Provisioner
import com.github.pshirshov.izumi.distage.model.{Locator, TheFactoryOfAllTheFactories}

class TheFactoryOfAllTheFactoriesDefaultImpl(
                                              provisioner: Provisioner
                                            ) extends TheFactoryOfAllTheFactories {
  override def produce(finalPlan: OrderedPlan, parentContext: Locator): Locator = {
    val dependencyMap = provisioner.provision(finalPlan, parentContext)

    new LocatorDefaultImpl(finalPlan, Option(parentContext), dependencyMap)
  }
}
