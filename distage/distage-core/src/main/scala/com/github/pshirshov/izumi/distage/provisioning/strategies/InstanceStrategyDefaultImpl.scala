package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.InstanceStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningContext, op: WiringOp.ReferenceInstance): Seq[OpResult] = {
    Seq(OpResult.NewInstance(op.target, op.wiring.instance))
  }
}
