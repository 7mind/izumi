package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan


trait Provisioner {
  def provision(dIPlan: OrderedPlan, parentContext: Locator): ProvisionImmutable
}
