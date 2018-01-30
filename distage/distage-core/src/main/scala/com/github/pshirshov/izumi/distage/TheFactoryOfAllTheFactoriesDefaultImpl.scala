package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.provisioning.Provisioner
import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.{Locator, TheFactoryOfAllTheFactories}

class TheFactoryOfAllTheFactoriesDefaultImpl(
                                              provisioner: Provisioner
                                            ) extends TheFactoryOfAllTheFactories {
  override def produce(finalPlan: FinalPlan, parentContext: Locator): Locator = {
    val dependencyMap = provisioner.provision(finalPlan, parentContext)

    new AbstractLocator {
      override val parent: Option[Locator] = Option(parentContext)

      override protected def unsafeLookup(key: RuntimeUniverse.DIKey): Option[Any] =
        dependencyMap.get(key)

      override def enumerate: Stream[IdentifiedRef] = dependencyMap.enumerate

      override val plan: FinalPlan = finalPlan
    }
  }
}
