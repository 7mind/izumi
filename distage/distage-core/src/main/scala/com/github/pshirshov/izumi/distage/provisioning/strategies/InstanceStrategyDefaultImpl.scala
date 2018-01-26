package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

class InstanceStrategyDefaultImpl extends InstanceStrategy {
  def getInstance(context: ProvisioningContext, op: ExecutableOp.WiringOp.ReferenceInstance): Seq[OpResult] = {
    Seq(OpResult.NewInstance(op.target, op.wiring.instance))
  }
}

object InstanceStrategyDefaultImpl {
  final val instance = new InstanceStrategyDefaultImpl()
}