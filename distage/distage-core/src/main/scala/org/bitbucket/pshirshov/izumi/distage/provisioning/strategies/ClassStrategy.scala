package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait ClassStrategy {
  def instantiateClass(context: ProvisioningContext, op: ExecutableOp.WiringOp.InstantiateClass): Seq[OpResult.NewInstance]

}
