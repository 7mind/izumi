package org.bitbucket.pshirshov.izumi.distage.provisioning

import org.bitbucket.pshirshov.izumi.distage.Locator
import org.bitbucket.pshirshov.izumi.distage.model.plan.FinalPlan



trait Provisioner {
  def provision(dIPlan: FinalPlan, parentContext: Locator): ProvisionImmutable
}
