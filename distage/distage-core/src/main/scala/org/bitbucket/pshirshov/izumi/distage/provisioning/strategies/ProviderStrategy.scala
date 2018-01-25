package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait ProviderStrategy {
  def callProvider(context: ProvisioningContext, op: ExecutableOp.WiringOp.CallProvider): Seq[OpResult.NewInstance]
}
