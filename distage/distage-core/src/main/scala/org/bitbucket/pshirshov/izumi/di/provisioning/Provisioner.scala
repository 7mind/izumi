package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.Locator
import org.bitbucket.pshirshov.izumi.di.model.plan.FinalPlan



trait Provisioner {
  def provision(dIPlan: FinalPlan, parentContext: Locator): ProvisionImmutable
}
