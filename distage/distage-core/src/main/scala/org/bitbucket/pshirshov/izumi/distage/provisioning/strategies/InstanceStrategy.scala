package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait InstanceStrategy {
  def getInstance(context: ProvisioningContext, op: ExecutableOp.WiringOp.ReferenceInstance): Seq[OpResult]
}
