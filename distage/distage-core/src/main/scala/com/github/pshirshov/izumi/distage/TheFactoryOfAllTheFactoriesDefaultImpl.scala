package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.provisioning.Provisioner

class TheFactoryOfAllTheFactoriesDefaultImpl(
                                              provisioner: Provisioner
                                            ) extends TheFactoryOfAllTheFactories {
  override def produce(finalPlan: FinalPlan, parentContext: Locator): Locator = {
    val dependencyMap = provisioner.provision(finalPlan, parentContext)

    new Locator {
      override val parent: Option[Locator] = Option(parentContext)

      override protected def unsafeLookup(key: DIKey): Option[Any] =
        dependencyMap.get(key)

      override def enumerate: Stream[IdentifiedRef] = dependencyMap.enumerate


      override val plan: FinalPlan = finalPlan
    }
  }
}
