package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.Locator
import com.github.pshirshov.izumi.distage.model.plan.FinalPlan



trait Provisioner {
  def provision(dIPlan: FinalPlan, parentContext: Locator): ProvisionImmutable
}
