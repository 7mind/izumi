package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

trait InstanceStrategy {
  def getInstance(context: ProvisioningContext, op: ExecutableOp.WiringOp.ReferenceInstance): Seq[OpResult]
}
