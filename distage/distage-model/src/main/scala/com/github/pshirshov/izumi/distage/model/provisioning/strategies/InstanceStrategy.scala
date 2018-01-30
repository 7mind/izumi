package com.github.pshirshov.izumi.distage.model.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

trait InstanceStrategy {
  def getInstance(context: ProvisioningContext, op: WiringOp.ReferenceInstance): Seq[OpResult]
}
