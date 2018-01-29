package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.FinalPlan


trait Provisioner {
  def provision(dIPlan: FinalPlan, parentContext: Locator): ProvisionImmutable
}
